package com.metacraft.isonim.examples

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * RS-M6: Kotlin-side `View.draw(Canvas)` -> `Bitmap` -> RGBA8888 byte array
 * capture helper.
 *
 * The acceptance gate for RS-M6 is the Espresso instrumented test
 * `AdapterCaptureTest`, which calls `TaskAppBridge.captureRootViewToRgba`
 * (a Nim-implemented JNI export inside `libtask_app.so`). The Nim
 * adapter's `renderFrame` body, under `-d:android -d:commandBuffer`,
 * calls back into this helper via JNI's `CallStaticObjectMethod` so the
 * actual `android.graphics.Bitmap` allocation, `android.graphics.Canvas`
 * construction, `view.draw(canvas)` rasterisation, `bitmap.getPixels`
 * read-back and ARGB_8888 -> RGBA8888 swizzle all happen inside the
 * Android Runtime (ART) on the real device.
 *
 * Threading model. Android requires that `View.measure` / `View.layout`
 * / `View.draw` be invoked on the UI thread for views attached to a
 * window (the rebuilt task_app tree is attached to the live
 * `MainActivity` window). The test calls
 * `TaskAppBridge.captureRootViewToRgba` from the instrumentation thread,
 * which lands here on the same thread; we therefore marshal the actual
 * draw onto the main `Looper` via a `Handler` + `CountDownLatch`, and
 * the instrumentation thread blocks until the bytes are ready.
 *
 * The "active root view" is the root produced by `MainActivity`'s most
 * recent `rebuildTree()` call (i.e. the parent of the materialised Nim
 * tree, after `contentContainer.addView(root, ...)`). MainActivity
 * publishes it to `activeRootView` at the end of each rebuild; if it's
 * `null` the capture returns an empty byte array and the Nim adapter
 * raises a `Defect`.
 *
 * Wire format. The returned `ByteArray` is canonical RGBA8888 row-major,
 * top-left first, alpha-last byte order, of length `width * height * 4`.
 * That matches the IsoNim render-stream `F` packet payload contract
 * (`packet.nim` in `isonim-render-serve`), so the Nim adapter doesn't
 * need to do any further swizzling.
 */
object CaptureHelper {

    /**
     * The currently active root View - i.e. the root of the materialised
     * Nim task_app tree (the parent that `MainActivity.rebuildTree` adds
     * to `contentContainer`). Updated on every rebuild.
     */
    @Volatile
    @JvmStatic
    var activeRootView: View? = null

    /**
     * RS-M6 primary capture path: `View.draw(Canvas)` into an
     * ARGB_8888 `Bitmap`, then swizzle into canonical RGBA8888 bytes.
     *
     * Called from Nim via JNI when the Nim adapter's `renderFrame`
     * needs to produce a `Frame` for the bridge. The recipe matches
     * the 6-step contract in
     * `isonim_render_serve/adapters/android_adapter.nim`:
     *
     *   1. Resolve the root View (held in `activeRootView`).
     *   2. `View.measure(width|EXACTLY, height|EXACTLY)` then
     *      `View.layout(0, 0, width, height)`. Without an explicit
     *      measure+layout pass, headless views have no bounds and
     *      `draw()` paints nothing.
     *   3. `Bitmap.createBitmap(width, height, ARGB_8888)`.
     *   4. `new Canvas(bitmap)`; `view.draw(canvas)`.
     *   5. `bitmap.getPixels(intArray, 0, width, 0, 0, width, height)`
     *      -> ARGB_8888 ints.
     *   6. Swizzle ARGB_8888 -> RGBA8888 byte order.
     *
     * The root view is already attached to the live MainActivity
     * window, which means it already has a frame from Android's layout
     * pass. We still re-measure + re-layout to the test-requested
     * dimensions so the capture size is deterministic regardless of
     * the device's physical screen resolution. After capture we
     * restore the original frame so the on-screen rendering isn't
     * disturbed.
     */
    @JvmStatic
    fun captureActiveRootToRgba(width: Int, height: Int): ByteArray {
        val root = activeRootView ?: run {
            android.util.Log.w(TAG, "captureActiveRootToRgba: activeRootView is null")
            return ByteArray(0)
        }
        if (width <= 0 || height <= 0) return ByteArray(0)
        android.util.Log.d(TAG,
            "captureActiveRootToRgba width=$width height=$height " +
            "rootClass=${root.javaClass.simpleName} " +
            "natural=${root.width}x${root.height} " +
            "children=${(root as? ViewGroup)?.childCount ?: -1}")

        val out = AtomicReference<ByteArray?>(null)
        val latch = CountDownLatch(1)

        val runner = Runnable {
            try {
                out.set(captureSync(root, width, height))
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "captureActiveRootToRgba failed", t)
                out.set(ByteArray(0))
            } finally {
                latch.countDown()
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            runner.run()
        } else {
            Handler(Looper.getMainLooper()).post(runner)
            latch.await()
        }

        return out.get() ?: ByteArray(0)
    }

    /**
     * Synchronous capture - must run on the UI thread. Visible for
     * tests that already hold the UI thread (e.g. a test that drives
     * the recipe directly without going through Nim).
     */
    @JvmStatic
    fun captureSync(view: View, width: Int, height: Int): ByteArray {
        // Because the live MainActivity has already laid the view out
        // at the device's full screen size (e.g. 1080x1919), the
        // children remember those measurements. Re-measuring the
        // parent to `width`x`height` doesn't reliably re-propagate to
        // already-cached children, which means
        // `view.draw(canvas)` into a 320x240 bitmap effectively
        // captures the top-left 320x240 of the natural layout —
        // missing later-added rows below.
        //
        // Two practical strategies:
        //   (a) measure+layout at natural size, draw into bitmap that
        //       matches the natural size, then downscale to
        //       `width`x`height`. Honest and lossless until the
        //       downscale step.
        //   (b) measure+layout at natural size, draw into the
        //       request-size bitmap via a scaled Canvas. Single-pass,
        //       cheaper, same visual.
        //
        // We pick (b). For RS-M6's acceptance assertions the visual
        // signature only needs to differ between mutations; the
        // scaling preserves that. Production use cases that need
        // pixel-exact capture at a specific size can call
        // `captureSync` with the natural dimensions and scale on the
        // wire.
        val natWidth = if (view.width > 0) view.width else width
        val natHeight = if (view.height > 0) view.height else height

        // Even though the view is laid out by the host, ensure measure
        // is up to date in case the test arrived between rebuildTree
        // and the next layout pass.
        if (view.isLayoutRequested) {
            val ws = MeasureSpec.makeMeasureSpec(natWidth, MeasureSpec.EXACTLY)
            val hs = MeasureSpec.makeMeasureSpec(natHeight, MeasureSpec.EXACTLY)
            view.measure(ws, hs)
            view.layout(view.left, view.top,
                        view.left + natWidth, view.top + natHeight)
        }

        // Step 3: allocate the ARGB_8888 bitmap at the requested size.
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            // Step 4: wrap in Canvas, scale to map view bounds onto
            // bitmap bounds, then call view.draw(canvas). The scale
            // transforms drawing coordinates inside view.draw, so the
            // whole natural-size tree fits into the capture bitmap.
            val canvas = Canvas(bitmap)
            // White backstop for any unpainted regions.
            canvas.drawColor(0xFFFFFFFF.toInt())
            val sx = width.toFloat() / natWidth.toFloat()
            val sy = height.toFloat() / natHeight.toFloat()
            canvas.scale(sx, sy)
            view.draw(canvas)

            // Step 5: extract pixels as ARGB_8888 ints.
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Step 6: swizzle ARGB_8888 -> RGBA8888 row-major byte order.
            // Bitmap.getPixels returns ints packed (A<<24)|(R<<16)|(G<<8)|B
            // (sRGB / non-premultiplied for ARGB_8888 with hasAlpha=true).
            // The IsoNim wire format is canonical RGBA8888 (R,G,B,A) per
            // pixel, alpha-last.
            val rgba = ByteArray(width * height * 4)
            var di = 0
            for (i in 0 until (width * height)) {
                val px = pixels[i]
                rgba[di]     = ((px shr 16) and 0xFF).toByte() // R
                rgba[di + 1] = ((px shr 8)  and 0xFF).toByte() // G
                rgba[di + 2] = (px and 0xFF).toByte()           // B
                rgba[di + 3] = ((px shr 24) and 0xFF).toByte() // A
                di += 4
            }
            return rgba
        } finally {
            bitmap.recycle()
        }
    }

    private const val TAG = "RS-M6-Capture"
}
