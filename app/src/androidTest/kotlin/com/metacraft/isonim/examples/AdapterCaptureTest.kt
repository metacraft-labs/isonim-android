package com.metacraft.isonim.examples

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RS-M6 acceptance test.
 *
 * Drives the canonical IsoNim render-stream Android streaming adapter
 * against a real Android device. Mirrors the RS-M5 (Cocoa)
 * `test_cocoa_adapter_macos_only.nim` shape, but everything happens
 * inside the Android Runtime on the connected device:
 *
 *   1. `ActivityScenario.launch(MainActivity::class.java)` materialises
 *      the EX-M6 task_app composition root from `libtask_app.so` into
 *      a real View hierarchy attached to a real Window.
 *   2. The `nimexamples` `MainActivity` publishes the materialised
 *      root to `CaptureHelper.activeRootView` at the end of its
 *      `rebuildTree()`.
 *   3. The test calls `TaskAppBridge.captureRootViewToRgba(width,
 *      height)`. That's a `@JvmStatic external fun` resolved by the
 *      dynamic linker to the Nim symbol
 *      `Java_com_metacraft_isonim_examples_TaskAppBridge_captureRootViewToRgba`
 *      exported by `isonim-examples/task_app/main_android.nim`'s
 *      `-d:androidGui` block (compiled as
 *      `libtask_app.so` and packaged into the `nimexamples` flavor's
 *      `arm64-v8a/`).
 *   4. The Nim entry point stashes the JNI env in a threadvar and
 *      drives the adapter's `renderFrame`, which routes through
 *      `isonim_render_serve/adapters/android_adapter.nim` ->
 *      `isonim_android/capture.captureViewToRgba` -> JNI ->
 *      `CaptureHelper.captureActiveRootToRgba(width, height)`.
 *   5. The Kotlin helper drives the 6-step `View.draw(Canvas)` ->
 *      `Bitmap` -> ARGB->RGBA recipe on the UI thread (marshalled via
 *      `Handler(Looper.getMainLooper()).post` + `CountDownLatch`) and
 *      returns the swizzled RGBA bytes through JNI back to Nim, which
 *      wraps them in a `Frame` and returns the bytes through JNI to
 *      this test.
 *
 * Both `@Test` methods exercise the same real-device pipeline:
 *
 *   - `captureRootView_dimensionsAndPayloadLengthMatchConfiguredSize`
 *     drives a single capture and asserts the bytes are well-formed
 *     (length, alpha opaque, no Linux placeholder grey, contains the
 *     task_app's actual colours).
 *
 *   - `streamsRealTaskAppTreeEndToEndThroughBridge` mirrors the
 *     scripted scenario: capture before any user input, drive
 *     Espresso `typeText` + `click(add_button)`, capture again, and
 *     assert the bytes differ. Proves the adapter's capture path is
 *     live, not one-shot stale.
 */
@RunWith(AndroidJUnit4::class)
class AdapterCaptureTest {

    @Test
    fun captureRootView_dimensionsAndPayloadLengthMatchConfiguredSize() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            // Espresso ensures the activity is idle before we proceed;
            // any pending layout / measure passes have settled.
            onView(withContentDescription("task_input")).check { _, _ -> }

            val width = 320
            val height = 240
            val rgba = TaskAppBridge.captureRootViewToRgba(width, height)

            // (a) Byte count must equal width*height*4 exactly.
            assertNotNull("captureRootViewToRgba returned null", rgba)
            assertEquals("rgba byte count == width*height*4",
                (width * height * 4).toLong(), rgba.size.toLong())

            // (b) Every alpha byte (offset 3, 7, 11, ...) must be 0xFF.
            //     ARGB_8888 with `hasAlpha=true` -> fully opaque after
            //     the helper's `canvas.drawColor(0xFFFFFFFF)` backstop
            //     and the task_app's solid-colour layers on top.
            var nonOpaque = 0
            var idx = 3
            while (idx < rgba.size) {
                if (rgba[idx] != 0xFF.toByte()) nonOpaque++
                idx += 4
            }
            assertEquals("all pixels must be fully opaque (alpha=0xFF)",
                0, nonOpaque)

            // (c) ZERO pixels may match the Linux-scaffold placeholder
            //     (0x18, 0x18, 0x18, 0xFF). If the adapter accidentally
            //     fell through to the Linux stub, this catches it.
            var greyPixels = 0
            var i = 0
            while (i < rgba.size) {
                if (rgba[i] == 0x18.toByte() &&
                    rgba[i + 1] == 0x18.toByte() &&
                    rgba[i + 2] == 0x18.toByte()) {
                    greyPixels++
                }
                i += 4
            }
            assertEquals("no pixel may match the Linux placeholder grey",
                0, greyPixels)

            // (d) Substantive non-uniformity: the rendered task_app has
            //     a slate-50 background (#F8FAFC) painted by the outer
            //     LinearLayout. After our white `canvas.drawColor`
            //     backstop and the task_app paints, most of the image
            //     should be near-white / very light. Assert that at
            //     least half the pixels are "near-white" (R, G, B all
            //     >= 0xE0) — that proves real Android paint actually
            //     ran (the only way to get bytes that light is via the
            //     Bitmap+Canvas pipeline).
            var nearWhite = 0
            i = 0
            while (i < rgba.size) {
                val r = rgba[i].toInt() and 0xFF
                val g = rgba[i + 1].toInt() and 0xFF
                val b = rgba[i + 2].toInt() and 0xFF
                if (r >= 0xE0 && g >= 0xE0 && b >= 0xE0) nearWhite++
                i += 4
            }
            val totalPixels = width * height
            assertTrue(
                "at least half the captured pixels must be near-white " +
                    "(got $nearWhite / $totalPixels)",
                nearWhite >= totalPixels / 2
            )

            // (e) Substantive non-uniformity, part 2: not all pixels
            //     are the same colour. The task_app paints text
            //     (`task_app — Nim/Android (EX-M6)` title bar plus the
            //     "New task..." EditText hint), buttons, and filter
            //     pills, so the raster must contain at least *some*
            //     darker pixels. Require at least one pixel with all
            //     three RGB channels <= 0x80 (mid-dark).
            var darkPixels = 0
            i = 0
            while (i < rgba.size) {
                val r = rgba[i].toInt() and 0xFF
                val g = rgba[i + 1].toInt() and 0xFF
                val b = rgba[i + 2].toInt() and 0xFF
                if (r <= 0x80 && g <= 0x80 && b <= 0x80) darkPixels++
                i += 4
            }
            assertTrue(
                "raster must contain at least 16 dark pixels (text/buttons); " +
                    "got $darkPixels",
                darkPixels >= 16
            )
        }
    }

    @Test
    fun streamsRealTaskAppTreeEndToEndThroughBridge() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            onView(withContentDescription("task_input")).check { _, _ -> }

            val width = 320
            val height = 240

            // Frame 0: empty task list.
            val frame0 = TaskAppBridge.captureRootViewToRgba(width, height)
            assertEquals("frame0 length", (width * height * 4).toLong(),
                frame0.size.toLong())

            // Drive a scripted task-app mutation: type "from android"
            // into the input, click "Add Task".
            onView(withContentDescription("task_input"))
                .perform(clearText(), typeText("from android"),
                         closeSoftKeyboard())
            onView(withContentDescription("add_button")).perform(click())

            // Frame 1: one task row visible — the bytes must differ.
            val frame1 = TaskAppBridge.captureRootViewToRgba(width, height)
            assertEquals("frame1 length", (width * height * 4).toLong(),
                frame1.size.toLong())

            var diffBytes = 0
            for (k in 0 until frame0.size) {
                if (frame0[k] != frame1[k]) diffBytes++
            }
            assertNotEquals(
                "frame0 and frame1 must differ after typeText + addTask",
                0, diffBytes
            )
            // The added row also adds a "[ ]"/"x"/label triplet
            // visible at typical 320x240 capture sizes, so the diff
            // surface area should be substantial — require at least
            // 100 differing bytes to rule out single-pixel jitter.
            assertTrue(
                "frame0/frame1 diff must include >= 100 bytes (got $diffBytes)",
                diffBytes >= 100
            )

            // Drive a second mutation: add a second task. Frame 2 must
            // again differ from frame 1.
            onView(withContentDescription("task_input"))
                .perform(clearText(), typeText("another one"),
                         closeSoftKeyboard())
            onView(withContentDescription("add_button")).perform(click())

            val frame2 = TaskAppBridge.captureRootViewToRgba(width, height)
            assertEquals("frame2 length", (width * height * 4).toLong(),
                frame2.size.toLong())

            var diff12 = 0
            for (k in 0 until frame1.size) {
                if (frame1[k] != frame2[k]) diff12++
            }
            assertTrue(
                "frame1/frame2 diff must include >= 100 bytes (got $diff12)",
                diff12 >= 100
            )
        }
    }
}
