package com.metacraft.isonim.examples

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Layer-4 composition root for the `nimexamples` product flavor.
 *
 * Loads `libtask_app.so` (cross-compiled from
 * `isonim-examples/task_app/main_android.nim` via
 * `just demo-build-android`), calls
 * `TaskAppBridge.buildTaskAppUI()` to populate the Nim command buffer
 * with the shared `task_app` view tree, then materialises the buffer
 * into a tree of real `android.view.View` instances.
 *
 * This mirrors the legacy
 * `com.metacraft.isonim.android.NimBridge.executeCommandBuffer`
 * pattern (`NimBridge.kt` in `app/src/main/kotlin/...`), but talks to a
 * different `.so` via a different JNI namespace so the two flavors can
 * coexist on the same device.
 *
 * On any UI event, the Nim VM mutates its state via the registered
 * click handler (`leaves.nim` `makeAddTaskHandler` / `makeToggleHandler`
 * / `makeRemoveHandler` / `makeFilterClickHandler`), then `rerender(vm)`
 * appends diff operations to the command buffer. Rather than apply
 * those diffs in-place, we call `rebuildTaskAppUI()` which resets the
 * buffer + callback registry and re-walks the whole tree — simplest
 * shape, and the one Espresso assertions can rely on (no stale handle
 * mappings, no half-rebuilt subtrees). The Nim VM persists across
 * rebuilds.
 *
 * **Stable Espresso descriptors.** Every interactive view gets a
 * deterministic `contentDescription` so Espresso can `onView(
 * withContentDescription(...))` against the real device's view tree:
 *
 *   - `task_input` — the EditText for new tasks.
 *   - `add_button` — the Nim `<button>` with text "Add Task".
 *   - `filter_all` / `filter_active` / `filter_completed` — the three
 *     filter pills (matched by `data-filter` attribute).
 *   - `task_row_<index>` — each visible task row (index counted in the
 *     order the leaves emitted them under the `<ul>`/list container).
 *   - `task_label_<index>` — the `<span>` inside the row carrying the
 *     task's name.
 *   - `toggle_<index>` — the first button inside the row (marker
 *     `[ ]` / `[x]`).
 *   - `remove_<index>` — the second button (`x`).
 *
 * Test assertions are in
 * `app/src/androidTest/kotlin/com/metacraft/isonim/examples/TaskAppScenarioTest.kt`.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var contentContainer: FrameLayout

    /** Map from Nim ViewHandle (int64) to its materialised Android View. */
    private val views = mutableMapOf<Long, View>()

    /**
     * Cached parent->child order, in the order the Nim leaves emitted
     * `appendChild`. We need this to assign stable indices for
     * Espresso `task_row_<i>` / `toggle_<i>` / etc. tags after the
     * tree is built, since the views' actual order under `task-list`
     * is what defines "the first visible task".
     */
    private val childOrder = mutableMapOf<Long, MutableList<Long>>()

    /** Per-view attribute bag (mirrors the Nim renderer's setAttribute calls). */
    private val attrs = mutableMapOf<Long, MutableMap<String, String>>()

    /** Root handle of the Nim view tree (first createView call). */
    private var rootHandle: Long = 0

    /** Handle of the `<ul class="task-list">` node, looked up via attrs. */
    private var listHandle: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // For the EX-M6 instrumented test the device may be sitting on
        // the keyguard PIN screen. `setShowWhenLocked(true)` + the
        // FLAG_DISMISS_KEYGUARD/SHOW_WHEN_LOCKED window flags ensure
        // the activity surfaces above the keyguard so Espresso can
        // reach RESUMED. Same trick the legacy native flavor uses for
        // its connected-device tests; safe for the standard app launch
        // path (without keyguard set, it's a no-op).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF8FAFC.toInt())
        }
        val titleBar = TextView(this).apply {
            text = "task_app — Nim/Android (EX-M6)"
            textSize = 18f
            setTextColor(0xFF0F172A.toInt())
            setPadding(dp(16), dp(40), dp(16), dp(8))
        }
        outer.addView(titleBar)

        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        outer.addView(contentContainer)
        setContentView(outer)

        rebuildTree()
    }

    /**
     * Tear down the previously materialised view tree and rebuild it
     * from a fresh Nim command buffer.
     *
     * - First call (after `super.onCreate`): `buildTaskAppUI` constructs
     *   a brand-new VM and runs the composition root.
     * - Subsequent calls (after a user event): `rebuildTaskAppUI`
     *   resets the buffer + callback registry, then re-runs the
     *   composition root against the *same* VM. The VM's signals carry
     *   the mutated state through, so the second tree reflects the
     *   click.
     */
    private fun rebuildTree(initial: Boolean = false) {
        views.clear()
        childOrder.clear()
        attrs.clear()
        rootHandle = 0
        listHandle = 0

        val count = if (initial || !rebuiltOnce) {
            rebuiltOnce = true
            TaskAppBridge.buildTaskAppUI()
        } else {
            TaskAppBridge.rebuildTaskAppUI()
        }
        executeCommands(count)

        // Find the root and the task-list node by attribute scan.
        val root = views[rootHandle] ?: return
        contentContainer.removeAllViews()
        (root.parent as? ViewGroup)?.removeView(root)
        contentContainer.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // After the tree is in place, walk attrs to assign stable
        // Espresso descriptors. We do this in a second pass because
        // `task_row_<i>` indices depend on the final child order under
        // the list node, which is only fully known once all
        // `appendChild` commands have executed.
        assignStableDescriptors()

        android.util.Log.d("RS-M6-Capture", "rebuildTree complete: " +
            "views.size=${views.size} rootHandle=$rootHandle " +
            "contentContainerChildren=${contentContainer.childCount}")
        // RS-M6: publish the materialised root to the capture helper so
        // `TaskAppBridge.captureRootViewToRgba` (called from Espresso
        // tests via the Nim adapter) can drive `View.draw(Canvas)`
        // against the live view tree. We expose `contentContainer`
        // rather than `root` directly because the test deliberately
        // requests capture dimensions that may not match the natural
        // size of the inner Nim tree; capturing the container lets
        // the helper re-measure / re-layout it to the requested size
        // without disturbing the rest of the activity chrome (title
        // bar etc.).
        CaptureHelper.activeRootView = contentContainer
    }

    private var rebuiltOnce = false

    override fun onDestroy() {
        // RS-M6: drop the capture-helper's reference so the View tree
        // isn't kept alive past the activity's lifecycle.
        if (CaptureHelper.activeRootView === contentContainer) {
            CaptureHelper.activeRootView = null
        }
        super.onDestroy()
    }

    /**
     * Walk the Nim command buffer and translate each command into the
     * corresponding Android View operation. Faithful port of the
     * legacy `NimBridge.executeCommandBuffer` for the new JNI
     * namespace, trimmed to the subset the canonical `task_app`
     * actually emits.
     */
    private fun executeCommands(count: Int) {
        for (i in 0 until count) {
            val kind = TaskAppBridge.getCommandKind(i)
            when (kind) {
                "createView" -> {
                    val nimHandle = TaskAppBridge.getCommandHandle(i)
                    val tag = TaskAppBridge.getCommandTag(i)
                    val v = createView(tag)
                    views[nimHandle] = v
                    if (rootHandle == 0L) rootHandle = nimHandle
                }
                "createScrollView" -> {
                    val nimHandle = TaskAppBridge.getCommandHandle(i)
                    val v = android.widget.ScrollView(this)
                    views[nimHandle] = v
                    if (rootHandle == 0L) rootHandle = nimHandle
                }
                "createRecyclerView" -> {
                    val nimHandle = TaskAppBridge.getCommandHandle(i)
                    val v = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    views[nimHandle] = v
                    if (rootHandle == 0L) rootHandle = nimHandle
                }
                "setText" -> {
                    val nimHandle = TaskAppBridge.getCommandHandle(i)
                    val value = TaskAppBridge.getCommandValue(i)
                    val v = views[nimHandle] ?: continue
                    if (v is TextView) v.text = value
                }
                "appendChild" -> {
                    val pH = TaskAppBridge.getCommandParentHandle(i)
                    val cH = TaskAppBridge.getCommandChildHandle(i)
                    val parent = views[pH] as? ViewGroup ?: continue
                    val child = views[cH] ?: continue
                    (child.parent as? ViewGroup)?.removeView(child)
                    parent.addView(child)
                    childOrder.getOrPut(pH) { mutableListOf() }.add(cH)
                }
                "removeChild" -> {
                    val pH = TaskAppBridge.getCommandParentHandle(i)
                    val cH = TaskAppBridge.getCommandChildHandle(i)
                    val parent = views[pH] as? ViewGroup ?: continue
                    val child = views[cH] ?: continue
                    parent.removeView(child)
                    childOrder[pH]?.remove(cH)
                }
                "insertBefore" -> {
                    val pH = TaskAppBridge.getCommandParentHandle(i)
                    val cH = TaskAppBridge.getCommandChildHandle(i)
                    val rH = TaskAppBridge.getCommandRefHandle(i)
                    val parent = views[pH] as? ViewGroup ?: continue
                    val child = views[cH] ?: continue
                    val ref = views[rH] ?: continue
                    val idx = parent.indexOfChild(ref)
                    (child.parent as? ViewGroup)?.removeView(child)
                    if (idx >= 0) parent.addView(child, idx) else parent.addView(child)
                    childOrder.getOrPut(pH) { mutableListOf() }.add(cH)
                }
                "setAttribute" -> {
                    val nimHandle = TaskAppBridge.getCommandHandle(i)
                    val name = TaskAppBridge.getCommandName(i)
                    val value = TaskAppBridge.getCommandValue(i)
                    attrs.getOrPut(nimHandle) { mutableMapOf() }[name] = value
                    val v = views[nimHandle] ?: continue
                    when (name) {
                        "placeholder" -> if (v is EditText) v.hint = value
                        "value" -> if (v is EditText) {
                            // Suppress the watcher during programmatic
                            // updates so we don't infinite-loop the
                            // VM/EditText mirror.
                            v.tag = SUPPRESS_WATCHER
                            v.setText(value)
                            v.tag = null
                        } else if (v is TextView) v.text = value
                        "class" -> {
                            // The leaves use `class="completed"` to
                            // flag toggled rows and `class="selected"`
                            // to flag the active filter pill. Surface
                            // both as Espresso-discoverable attributes
                            // via tag + content-description suffix
                            // assigned in `assignStableDescriptors`.
                        }
                        "data-filter", "data-task-id", "data-app" -> { /* read in second pass */ }
                    }
                }
                "setStyle" -> {
                    // The `task_app` leaves don't currently emit
                    // style commands — they ship classnames only —
                    // so we accept and ignore for now.
                }
                "setEventListener" -> {
                    val nimHandle = TaskAppBridge.getCommandHandle(i)
                    val event = TaskAppBridge.getCommandEvent(i)
                    val callbackId = TaskAppBridge.getCommandCallbackId(i)
                    val v = views[nimHandle] ?: continue
                    when (event) {
                        "click" -> v.setOnClickListener {
                            onNimEvent(callbackId)
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle a click on a Nim-registered view. The pattern is:
     *
     *  1. If this is the `add_button`, copy the EditText's text into
     *     the VM first (`TaskAppBridge.setInputText`) so the Nim
     *     click handler sees it. The leaves' `makeAddTaskHandler`
     *     calls `vm.addTask(vm.inputText.val)`.
     *  2. Fire the callback into Nim (`TaskAppBridge.handleEvent`).
     *     Nim mutates the VM and re-renders into the (now-soon-stale)
     *     command buffer.
     *  3. Rebuild the whole tree from a fresh buffer.
     */
    private fun onNimEvent(callbackId: Int) {
        // Sync the EditText's current text into the VM first. We do
        // this unconditionally on every click; if the click was on a
        // non-Add view, it's a harmless no-op because the leaves'
        // toggle/remove/filter handlers don't read `inputText`.
        val input = findInputEditText()
        if (input != null) {
            TaskAppBridge.setInputText(input.text.toString())
        }
        TaskAppBridge.handleEvent(callbackId)
        rebuildTree()
    }

    private fun findInputEditText(): EditText? {
        for ((h, v) in views) {
            val tag = attrs[h]?.get("class")
            // The taskInput leaf emits an `<input>` (= EditText) and
            // doesn't tag it with a class; the unique discriminator
            // is its `placeholder` attribute ("New task...").
            if (v is EditText && attrs[h]?.get("placeholder") == "New task...") {
                return v
            }
        }
        return null
    }

    /**
     * Second pass: assign stable Espresso descriptors to every
     * interactive view, derived from attrs the leaves attached
     * (`data-filter`, `class`, `placeholder`, ...) and from the final
     * child order under the list node.
     */
    private fun assignStableDescriptors() {
        // 1. The text input. The leaves emit `placeholder="New task..."`.
        for ((h, v) in views) {
            if (v is EditText && attrs[h]?.get("placeholder") == "New task...") {
                v.contentDescription = "task_input"
                // Mirror typed text into the VM as the user types so
                // the IME-action gap (no Android submit event from the
                // <input>) is bridged before any click handler reads
                // `vm.inputText.val`. The watcher is suppressed during
                // programmatic `setText` (see SUPPRESS_WATCHER guard).
                if (v.tag != WATCHER_INSTALLED) {
                    v.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            if (v.tag == SUPPRESS_WATCHER) return
                            TaskAppBridge.setInputText(s?.toString() ?: "")
                        }
                    })
                    v.tag = WATCHER_INSTALLED
                }
            }
        }

        // 2. The Add-Task button. Identified by tag+text "Add Task".
        for ((h, v) in views) {
            if (v is Button) {
                val text = (v as TextView).text?.toString() ?: ""
                if (text == "Add Task") v.contentDescription = "add_button"
            }
        }

        // 3. The three filter buttons. The leaves tag each with
        //    `data-filter="All"|"Active"|"Completed"`.
        for ((h, v) in views) {
            when (attrs[h]?.get("data-filter")) {
                "All" -> v.contentDescription = "filter_all"
                "Active" -> v.contentDescription = "filter_active"
                "Completed" -> v.contentDescription = "filter_completed"
            }
        }

        // 4. Task rows. The leaves create one `<li>` per visible task
        //    under the list node; the list node carries
        //    `class="task-list"`. Walk children of that node in order
        //    and assign `task_row_<i>` / `toggle_<i>` / `remove_<i>` /
        //    `task_label_<i>`.
        val listHandle = views.keys.firstOrNull { attrs[it]?.get("class") == "task-list" }
        if (listHandle != null) {
            val rows = childOrder[listHandle] ?: emptyList()
            rows.forEachIndexed { idx, rowH ->
                val row = views[rowH] ?: return@forEachIndexed
                row.contentDescription = "task_row_$idx"
                val rowChildren = childOrder[rowH] ?: emptyList()
                // Empty-state placeholder is a `<p>` (TextView), not
                // a `<li>` with sub-children. Skip annotation when the
                // row has no children — it's the placeholder.
                if (rowChildren.isEmpty()) {
                    // Single-line placeholder. Tag it so the test can
                    // assert visibility.
                    row.contentDescription = "task_empty_state"
                    return@forEachIndexed
                }
                // Child shape from leaves.nim:
                //   children[0] = toggle button ([ ] / [x])
                //   children[1] = label span
                //   children[2] = remove button (x)
                rowChildren.getOrNull(0)?.let { h ->
                    views[h]?.contentDescription = "toggle_$idx"
                }
                rowChildren.getOrNull(1)?.let { h ->
                    views[h]?.contentDescription = "task_label_$idx"
                }
                rowChildren.getOrNull(2)?.let { h ->
                    views[h]?.contentDescription = "remove_$idx"
                }
            }
        }
    }

    /**
     * Map a Nim renderer tag (already lowered to an Android
     * widget-class name by `isonim_android/renderer`'s `mapTag`) into
     * a real View instance. Reuses the same `tagMap` codomain the
     * legacy `NimBridge.createView` switches on.
     */
    private fun createView(androidTag: String): View {
        val ctx: Context = this
        return when (androidTag) {
            "FrameLayout" -> LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            "LinearLayout" -> LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            "TextView" -> TextView(ctx).apply {
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setTextColor(0xFF0F172A.toInt())
            }
            "MaterialButton" -> try {
                MaterialButton(ctx).apply {
                    isAllCaps = false
                    minHeight = dp(40)
                    minWidth = dp(48)
                }
            } catch (_: Exception) {
                Button(ctx).apply { isAllCaps = false }
            }
            "Button" -> Button(ctx).apply { isAllCaps = false }
            "EditText" -> EditText(ctx).apply {
                setSingleLine()
                hint = "New task..."
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            else -> LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    companion object {
        private val SUPPRESS_WATCHER = Any()
        private val WATCHER_INSTALLED = Any()
    }
}
