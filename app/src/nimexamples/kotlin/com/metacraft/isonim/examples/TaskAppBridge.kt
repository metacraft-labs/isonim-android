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
}
