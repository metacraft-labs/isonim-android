package com.metacraft.isonim.examples

/**
 * Kotlin companion for the JNI surface exported by
 * `isonim-examples/settings_app/main_android_entry.nim` (compiled to
 * `libsettings_app.so`).
 *
 * EX-M22 architectural decision (B): the settings_app surface ships as
 * a SEPARATE shared library alongside `libtask_app.so`, so the EX-M6
 * task_app surface stays byte-untouched. Both libs are loaded into the
 * same Android process; their JNI symbols are namespaced
 * (`Java_*_SettingsAppBridge_*` vs. `Java_*_TaskAppBridge_*`) so they
 * never collide.
 *
 * Surface shape mirrors [TaskAppBridge] — build/rebuild the UI, read
 * the command buffer back, fire events, capture frames via RS-M6's
 * adapter chain. The settings_app does not need a `setInputText`
 * hook (there's no text input in the demo's UI surface).
 */
object SettingsAppBridge {

    init { System.loadLibrary("settings_app") }

    @JvmStatic external fun buildSettingsAppUI(): Int
    @JvmStatic external fun rebuildSettingsAppUI(): Int

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

    @JvmStatic external fun handleEvent(callbackId: Int)

    /**
     * RS-M6 Android adapter capture entry point.
     *
     * Drives the Nim adapter's `renderFrame` against the currently
     * displayed settings_app tree (published to
     * `CaptureHelper.activeRootView` by `MainActivity.rebuildTree`)
     * and returns the rendered pixels as canonical RGBA8888 row-major
     * bytes. Length is exactly `width * height * 4`. Empty array
     * indicates capture failure.
     *
     * Acceptance test:
     *   app/src/androidTest/kotlin/com/metacraft/isonim/examples/
     *   SettingsAppScenarioTest.kt
     */
    @JvmStatic external fun captureRootViewToRgba(width: Int, height: Int): ByteArray
}
