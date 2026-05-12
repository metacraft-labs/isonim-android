package com.metacraft.isonim.examples

/**
 * Kotlin companion for the JNI surface exported by
 * `isonim-examples/task_app/main_android.nim` (compiled to
 * `libtask_app.so`).
 *
 * Mirrors the legacy `com.metacraft.isonim.android.NimBridge`
 * external-fun block but uses the `TaskAppBridge` JNI namespace so the
 * `nimexamples` flavor's `libtask_app.so` can co-exist with the legacy
 * flavors' `libisonim.so` on the same device.
 *
 * The surface is intentionally minimal: build / rebuild the UI, read
 * the command buffer back, fire events, and push the EditText's
 * contents back into the VM before clicking Add (because the Android
 * `<input>` leaf has no native submit event yet — see the
 * `task_app/android/leaves.nim` "API gap" note).
 */
object TaskAppBridge {

    init { System.loadLibrary("task_app") }

    @JvmStatic external fun buildTaskAppUI(): Int
    @JvmStatic external fun rebuildTaskAppUI(): Int

    @JvmStatic external fun getCommandCount(): Int
    @JvmStatic external fun getCommandKind(index: Int): String
    @JvmStatic external fun getCommandHandle(index: Int): Long
    @JvmStatic external fun getCommandTag(index: Int): String
    @JvmStatic external fun getCommandName(index: Int): String
    @JvmStatic external fun getCommandValue(index: Int): String
    @JvmStatic external fun getCommandParentHandle(index: Int): Long
    @JvmStatic external fun getCommandChildHandle(index: Int): Long
    @JvmStatic external fun getCommandRefHandle(index: Int): Long
    @JvmStatic external fun getCommandCallbackId(index: Int): Int
    @JvmStatic external fun getCommandEvent(index: Int): String
    @JvmStatic external fun getCommandTitle(index: Int): String
    @JvmStatic external fun getCommandMessage(index: Int): String
    @JvmStatic external fun getCommandButtonCount(index: Int): Int

    @JvmStatic external fun handleEvent(callbackId: Int)
    @JvmStatic external fun setInputText(text: String)

    /**
     * RS-M6 Android adapter capture entry point.
     *
     * Drives the Nim adapter's `renderFrame` against the currently
     * displayed task_app tree (published to
     * `CaptureHelper.activeRootView` by `MainActivity.rebuildTree`)
     * and returns the rendered pixels as canonical RGBA8888 row-major
     * bytes. Length is exactly `width * height * 4`. Empty array
     * indicates capture failure (no active root, JNI error,
     * out-of-bounds dimensions).
     *
     * The Nim implementation lives in
     * `isonim-examples/task_app/main_android.nim`'s `-d:androidGui`
     * block; it calls back into `CaptureHelper.captureActiveRootToRgba`
     * via JNI to do the actual `Bitmap.createBitmap` /
     * `Canvas(bitmap)` / `view.draw(canvas)` /
     * `bitmap.getPixels` / ARGB->RGBA swizzle. This round-trip
     * (Kotlin -> JNI -> Nim adapter -> JNI -> Kotlin helper) is
     * deliberate: it exercises the same Nim adapter code-path the
     * IsoNim render-stream bridge will use when streaming a real
     * device-rendered task_app to a remote browser canvas.
     *
     * Acceptance test:
     *   app/src/androidTest/kotlin/com/metacraft/isonim/examples/
     *   AdapterCaptureTest.kt
     */
    @JvmStatic external fun captureRootViewToRgba(width: Int, height: Int): ByteArray
}
