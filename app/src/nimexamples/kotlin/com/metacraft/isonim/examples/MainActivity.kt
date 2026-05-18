package com.metacraft.isonim.examples

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
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

    /**
     * EX-M22: which demo this Activity instance is hosting. Set from
     * the launch Intent's `demo` string extra (default "tasks" to
     * preserve EX-M6 behavior when the extra is absent).
     *
     *   - `"tasks"`    (or anything else, or absent) → task_app via
     *                  `libtask_app.so` and the [TaskAppBridge] JNI
     *                  namespace. This is the EX-M6 path.
     *   - `"settings"` → settings_app via `libsettings_app.so` and the
     *                  [SettingsAppBridge] JNI namespace (EX-M22).
     */
    private var demoMode: String = "tasks"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // EX-M22: read the demo selector from the launch Intent before
        // any UI plumbing. When the extra is missing we keep the
        // EX-M6 default ("tasks") so existing callers see no change.
        demoMode = intent?.getStringExtra("demo")?.takeIf { it.isNotEmpty() }
            ?: "tasks"

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

        // Round-3 fix: drop the debug title bar entirely (the
        // reviewer flagged the `task_app` / `settings_app` heading
        // as dev chrome that has no place on the demo surface). The
        // outer container also moves to the dark Material 3 surface
        // tone the leaves now style their cards against, and
        // contributes the top status-bar padding the title bar used
        // to provide.
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111118.toInt())
            setPadding(dp(12), dp(40), dp(12), dp(12))
        }

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
            when (demoMode) {
                "settings" -> SettingsAppBridge.buildSettingsAppUI()
                else -> TaskAppBridge.buildTaskAppUI()
            }
        } else {
            when (demoMode) {
                "settings" -> SettingsAppBridge.rebuildSettingsAppUI()
                else -> TaskAppBridge.rebuildTaskAppUI()
            }
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

    // ----- EX-M22: per-demo command-buffer reader delegation -----
    //
    // The settings_app and task_app each export their own JNI
    // namespace (`SettingsAppBridge_*` vs. `TaskAppBridge_*`); both
    // serialise their command buffers with identical shapes (the
    // `isonim_android/command_buffer` module is shared between the
    // two libs). These thin readers dispatch to the right namespace
    // based on `demoMode`, preserving the EX-M6 task_app path
    // byte-for-byte when the Intent extra is absent.

    private fun cmdKind(i: Int): String = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandKind(i)
        else -> TaskAppBridge.getCommandKind(i)
    }
    private fun cmdHandle(i: Int): Long = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandHandle(i)
        else -> TaskAppBridge.getCommandHandle(i)
    }
    private fun cmdTag(i: Int): String = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandTag(i)
        else -> TaskAppBridge.getCommandTag(i)
    }
    private fun cmdName(i: Int): String = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandName(i)
        else -> TaskAppBridge.getCommandName(i)
    }
    private fun cmdValue(i: Int): String = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandValue(i)
        else -> TaskAppBridge.getCommandValue(i)
    }
    private fun cmdParent(i: Int): Long = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandParentHandle(i)
        else -> TaskAppBridge.getCommandParentHandle(i)
    }
    private fun cmdChild(i: Int): Long = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandChildHandle(i)
        else -> TaskAppBridge.getCommandChildHandle(i)
    }
    private fun cmdRef(i: Int): Long = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandRefHandle(i)
        else -> TaskAppBridge.getCommandRefHandle(i)
    }
    private fun cmdEvent(i: Int): String = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandEvent(i)
        else -> TaskAppBridge.getCommandEvent(i)
    }
    private fun cmdCallback(i: Int): Int = when (demoMode) {
        "settings" -> SettingsAppBridge.getCommandCallbackId(i)
        else -> TaskAppBridge.getCommandCallbackId(i)
    }
    private fun handleEvent(callbackId: Int) = when (demoMode) {
        "settings" -> SettingsAppBridge.handleEvent(callbackId)
        else -> TaskAppBridge.handleEvent(callbackId)
    }

    /**
     * Walk the Nim command buffer and translate each command into the
     * corresponding Android View operation. Faithful port of the
     * legacy `NimBridge.executeCommandBuffer` for the new JNI
     * namespace, trimmed to the subset the canonical `task_app`
     * actually emits. EX-M22 generalises the reads via the
     * `cmd*` delegators so the settings_app's command stream lands
     * in the same translator without duplicating the body.
     */
    private fun executeCommands(count: Int) {
        for (i in 0 until count) {
            val kind = cmdKind(i)
            when (kind) {
                "createView" -> {
                    val nimHandle = cmdHandle(i)
                    val tag = cmdTag(i)
                    val v = createView(tag)
                    views[nimHandle] = v
                    if (rootHandle == 0L) rootHandle = nimHandle
                }
                "createScrollView" -> {
                    val nimHandle = cmdHandle(i)
                    val v = android.widget.ScrollView(this)
                    views[nimHandle] = v
                    if (rootHandle == 0L) rootHandle = nimHandle
                }
                "createRecyclerView" -> {
                    val nimHandle = cmdHandle(i)
                    val v = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    views[nimHandle] = v
                    if (rootHandle == 0L) rootHandle = nimHandle
                }
                "setText" -> {
                    val nimHandle = cmdHandle(i)
                    val value = cmdValue(i)
                    val v = views[nimHandle] ?: continue
                    // M-EVP-14 round-7 fix: a `CheckBox` extends
                    // `TextView`, so a naive `v.text = value` would
                    // render the leaves' check-glyph fallback ("✓") as
                    // the label text NEXT to the checkbox square. The
                    // CheckBox's visual state is driven by `isChecked`
                    // (set via the `checked` setAttribute path); we
                    // explicitly clear the label so the box itself is
                    // the only affordance.
                    if (v is CheckBox) v.text = ""
                    else if (v is TextView) v.text = value
                }
                "appendChild" -> {
                    val pH = cmdParent(i)
                    val cH = cmdChild(i)
                    val parent = views[pH] as? ViewGroup ?: continue
                    val child = views[cH] ?: continue
                    (child.parent as? ViewGroup)?.removeView(child)
                    parent.addView(child)
                    childOrder.getOrPut(pH) { mutableListOf() }.add(cH)
                    // M-EVP-14 round 2: apply any pending flex-grow
                    // weight now that the parent's orientation is
                    // known.
                    flexGrowWeights[cH]?.let { applyFlexGrow(child, it) }
                    // M-EVP-14 round 2: respect parent's `gap` setStyle
                    // for any subsequent siblings. The first child gets
                    // no leading gap.
                    val gap = gapValues[pH]
                    if (gap != null && parent.childCount > 1) {
                        val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
                        if (lp != null) {
                            val gapPx = dp(gap)
                            val isHorizontal = parent is LinearLayout &&
                                parent.orientation == LinearLayout.HORIZONTAL
                            if (isHorizontal) lp.leftMargin = gapPx
                            else lp.topMargin = gapPx
                            child.layoutParams = lp
                        }
                    }
                }
                "removeChild" -> {
                    val pH = cmdParent(i)
                    val cH = cmdChild(i)
                    val parent = views[pH] as? ViewGroup ?: continue
                    val child = views[cH] ?: continue
                    parent.removeView(child)
                    childOrder[pH]?.remove(cH)
                }
                "insertBefore" -> {
                    val pH = cmdParent(i)
                    val cH = cmdChild(i)
                    val rH = cmdRef(i)
                    val parent = views[pH] as? ViewGroup ?: continue
                    val child = views[cH] ?: continue
                    val ref = views[rH] ?: continue
                    val idx = parent.indexOfChild(ref)
                    (child.parent as? ViewGroup)?.removeView(child)
                    if (idx >= 0) parent.addView(child, idx) else parent.addView(child)
                    childOrder.getOrPut(pH) { mutableListOf() }.add(cH)
                }
                "setAttribute" -> {
                    val nimHandle = cmdHandle(i)
                    val name = cmdName(i)
                    val value = cmdValue(i)
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
                        // M-EVP-14 round-7: the task-row leading
                        // `<checkbox>` mirrors `task.completed` via a
                        // `checked` attribute. Apply it to the real
                        // CheckBox so the captured frame shows the
                        // Material toggle visibly checked/unchecked.
                        "checked" -> if (v is CheckBox) {
                            v.isChecked =
                                value == "true" || value == "1"
                        }
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
                    // M-EVP-14 round 2: the `task_app` Android leaves
                    // now ship Material 3 style hints (filter chip
                    // fills, Add Task height, primary CTA color) via
                    // `setStyle` commands. The Nim-side AndroidRenderer
                    // maps the CSS keys to Android-style names before
                    // emitting (`background-color` -> `backgroundColor`,
                    // `flex-direction` -> `orientation`, etc.), so this
                    // dispatch keys on the Android-side names directly.
                    val nimHandle = cmdHandle(i)
                    val prop = cmdName(i)
                    val value = cmdValue(i)
                    val v = views[nimHandle] ?: continue
                    applyStyle(nimHandle, v, prop, value)
                }
                "setEventListener" -> {
                    val nimHandle = cmdHandle(i)
                    val event = cmdEvent(i)
                    val callbackId = cmdCallback(i)
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
        // non-Add view, it's a harmless no-op because the task_app's
        // toggle/remove/filter handlers don't read `inputText`. The
        // settings_app demo has no EditText widgets, so we skip the
        // sync entirely there.
        if (demoMode != "settings") {
            val input = findInputEditText()
            if (input != null) {
                TaskAppBridge.setInputText(input.text.toString())
            }
        }
        handleEvent(callbackId)
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
     * EX-M22: Second-pass descriptors for the settings_app demo. The
     * settings_app emits a different view tree shape than the task_app
     * (bottom-sheet drawer: header rows in a scrollable list + a single
     * bottom-sheet pane with the active group's items). The Espresso
     * test surface needs deterministic descriptors keyed by group_id /
     * item_id rather than the index-based scheme used for task rows.
     *
     * Tags (all settings descriptors use the `settings_` prefix to
     * avoid colliding with task_row_*, toggle_*, etc.):
     *
     *   - `settings_root` — the `data-app="settings-app"` root.
     *   - `settings_sheet_list` — the scrollable list of group rows.
     *   - `settings_sheet_row_<group_id>` — clickable group row.
     *   - `settings_sheet_header_<group_id>` — the group's `<header>`
     *     (the actual click target — clicking it activates the group).
     *   - `settings_bottom_sheet` — the bottom-sheet pane.
     *   - `settings_toggle_<item_id>` — toggle (checkbox) leaf.
     *   - `settings_number_<item_id>` — number input host.
     *   - `settings_choice_<item_id>` — choice select host.
     *
     * Item IDs are the catalog ids ("appearance.dark_mode" etc.) —
     * which the leaves stash on the value-bearing leaves via the
     * implicit `data-*` attributes (the toggle/number/choice host
     * carries the `id`? No — actually the per-kind component templates
     * do not pass the item id through as a data-* attribute. We walk
     * the bottom-sheet's children in order and pair each item to the
     * catalog's items by visible label, mirroring how the parity
     * driver scopes lookups.) For the Espresso test, locating an
     * item by visible label is the cleanest path because the visible
     * label IS what an end-user would target.
     */
    private fun assignSettingsDescriptors() {
        // 1. The root.
        for ((h, v) in views) {
            if (attrs[h]?.get("data-app") == "settings-app") {
                v.contentDescription = "settings_root"
            }
            if (attrs[h]?.get("class") == "settings-sheet-list") {
                v.contentDescription = "settings_sheet_list"
            }
            if (attrs[h]?.get("class") == "settings-bottom-sheet") {
                v.contentDescription = "settings_bottom_sheet"
            }
        }
        // 2. Per-group sheet rows + their header click targets.
        for ((h, v) in views) {
            val sheetId = attrs[h]?.get("data-sheet-id") ?: continue
            val cls = attrs[h]?.get("class") ?: ""
            if (cls.startsWith("settings-sheet-row")) {
                v.contentDescription = "settings_sheet_row_$sheetId"
            }
        }
        for ((h, v) in views) {
            val groupId = attrs[h]?.get("data-group-id") ?: continue
            if (attrs[h]?.get("class") == "settings-group-header") {
                v.contentDescription = "settings_sheet_header_$groupId"
            }
        }
        // 3. Item-row descriptors. The bottom-sheet pane's children
        //    are <div class="settings-item">. Each item-row's first
        //    child is the label (a <label class="settings-label">);
        //    the last child is the value-bearing leaf (checkbox /
        //    number host / choice host). We key descriptors by the
        //    visible label text — that's the surface the Espresso
        //    test can reasonably depend on.
        val sheetHandle = views.keys.firstOrNull {
            attrs[it]?.get("class") == "settings-bottom-sheet"
        } ?: return
        val itemRowHandles = childOrder[sheetHandle] ?: emptyList()
        for (rowH in itemRowHandles) {
            val rowChildren = childOrder[rowH] ?: continue
            if (rowChildren.isEmpty()) continue
            val labelH = rowChildren[0]
            val labelView = views[labelH] as? TextView ?: continue
            val labelText = labelView.text?.toString() ?: continue
            // Slug-friendly: lowercase + spaces to underscores.
            val slug = labelText.lowercase().replace(Regex("[^a-z0-9]+"), "_")
            // The last child is the value-bearing leaf. We descend
            // into it for toggle (checkbox itself) / number (the
            // inner <input type="number">) / choice (the inner
            // <select>) so the Espresso test can target the actual
            // clickable / data-bearing element.
            val leafH = rowChildren.last()
            val leafView = views[leafH] ?: continue
            val leafClass = attrs[leafH]?.get("class") ?: ""
            val leafType = attrs[leafH]?.get("type") ?: ""
            when {
                leafType == "checkbox" -> {
                    leafView.contentDescription = "settings_toggle_$slug"
                }
                leafClass == "settings-number" -> {
                    leafView.contentDescription = "settings_number_$slug"
                    // Annotate the inner <input> too.
                    val innerChildren = childOrder[leafH] ?: emptyList()
                    for (innerH in innerChildren) {
                        if (attrs[innerH]?.get("type") == "number") {
                            views[innerH]?.contentDescription =
                                "settings_number_input_$slug"
                        }
                    }
                }
                leafClass == "settings-choice" -> {
                    leafView.contentDescription = "settings_choice_$slug"
                    // Annotate the inner <select> (empty-class child).
                    val innerChildren = childOrder[leafH] ?: emptyList()
                    for (innerH in innerChildren) {
                        if ((attrs[innerH]?.get("class") ?: "") == "") {
                            views[innerH]?.contentDescription =
                                "settings_choice_select_$slug"
                        }
                    }
                }
            }
        }
    }

    /**
     * Second pass: assign stable Espresso descriptors to every
     * interactive view, derived from attrs the leaves attached
     * (`data-filter`, `class`, `placeholder`, ...) and from the final
     * child order under the list node.
     */
    private fun assignStableDescriptors() {
        if (demoMode == "settings") {
            assignSettingsDescriptors()
            return
        }
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
                // Round-3 fix: the activity's outer surface is now
                // dark (`#111118` on the surface card neutral). The
                // default TextView text color tracks that, so any
                // leaf that doesn't `setStyle(color, …)` explicitly
                // (e.g. the bottom-sheet group marker chevron in
                // `settings_app/android/shell.nim`) still renders
                // legibly on the new background.
                setTextColor(0xFFE6E6F0.toInt())
                // M-EVP-14 Wave-Q fix: leaf-emitted `font-size` values
                // are design-pixel (sp) units. The post-no-stretch
                // pipeline streams the device framebuffer at native
                // resolution into a centered 1:1 crop, so we need
                // density-aware text sizing — same convention as the
                // pre-Wave-P SP path used. SP also honors users'
                // accessibility font-scale.
                setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SP)
            }
            "MaterialButton" -> try {
                MaterialButton(ctx).apply {
                    isAllCaps = false
                    // M-EVP-14 Wave-Q fix: density-aware sp for label
                    // text (same rationale as the TextView branch).
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SP)
                    // Round-3 fix: drop the legacy 40 dp minHeight /
                    // 48 dp minWidth so per-leaf `setStyle("height",
                    // "32")` / `setStyle("width", "40")` calls (the
                    // M3 switch track / chip / circular stepper
                    // metrics in the round-3 leaves) actually take
                    // effect. The leaves now own their own size
                    // explicitly; no need for a one-size-fits-all
                    // floor here.
                    minHeight = 0
                    minWidth = 0
                    insetTop = 0
                    insetBottom = 0
                    // Round-3 fix: default `MaterialButton`
                    // backgroundTint to transparent. Leaves that are
                    // primary CTAs (Add Task) / active filter chips /
                    // active settings choice chips paint themselves
                    // indigo via `setStyle("background-color", …)`;
                    // every other button (per-row toggle, remove,
                    // inactive filter chip) inherited the M3 indigo
                    // tint by default and washed the round-3
                    // reviewer's screen.
                    backgroundTintList =
                        android.content.res.ColorStateList.valueOf(
                            Color.TRANSPARENT)
                }
            } catch (_: Exception) {
                Button(ctx).apply { isAllCaps = false }
            }
            "Button" -> Button(ctx).apply {
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SP)
            }
            // M-EVP-14 round-7 fix: Material `CheckBox` mapping for the
            // task-row leading toggle. The Nim leaves now emit a
            // `<checkbox>` element (mapped via the renderer's tagMap)
            // for each task row's leading edge so the row shows a
            // proper Material checkbox to the left of the task name
            // instead of an empty toggle slot. The CheckBox's checked
            // state is mirrored from the VM via `attr["checked"]`
            // applied in the setAttribute dispatch below.
            "CheckBox" -> CheckBox(ctx).apply {
                setTextColor(0xFFE6E6F0.toInt())
                // No padding so the 20-dp leading slot inside the row
                // doesn't get pushed off by Material's default insets.
                setPadding(0, 0, 0, 0)
                minimumWidth = 0
                minimumHeight = 0
                setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SP)
            }
            "EditText" -> EditText(ctx).apply {
                setSingleLine()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SP)
                // EX-M6 task_app input ships an explicit
                // placeholder="New task..." attribute via the leaves;
                // the renderer-method translates that to
                // `EditText.hint`. The default hint is empty so
                // settings_app's `<input type="number">` (which sets
                // no placeholder) doesn't inherit the task_app hint
                // cosmetically.
                hint = ""
                setPadding(dp(12), dp(8), dp(12), dp(8))
                // Round-3 fix: the activity surface is now dark, so
                // give EditText explicit on-surface text + hint
                // colours instead of the system-default near-black
                // that was nearly invisible on the new background.
                setTextColor(0xFFE6E6F0.toInt())
                setHintTextColor(0xFFA0A0B8.toInt())
            }
            // EX-M22: <select> maps to Spinner via the Android
            // renderer's tag table. For the EX-M22 settings_app demo
            // we don't need a real Spinner widget (the VM is the
            // source of truth for the active choice); a plain
            // LinearLayout with a visible minHeight is enough to make
            // the choice host pass Espresso's `isDisplayed()`. The
            // descriptor scan also walks the choice host's
            // <option> children, but since options map to FrameLayout
            // (= LinearLayout) which is not a TextView, their text
            // content stays invisible. The parity test exercises
            // selection through `setAttribute("data-value", ...)` +
            // `fireEvent("click", ...)` directly on the select node,
            // not via real Spinner interaction.
            "Spinner" -> LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = dp(24)
            }
            else -> LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                // EX-M22: ensure the fallback widget has visible
                // bounds so Espresso `isDisplayed()` matches when
                // descriptors are assigned to the host. Without this,
                // empty/un-mapped widgets render with 0 height and
                // become invisible to UI tests even though they
                // exist in the tree.
                minimumHeight = dp(8)
            }
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    /**
     * Per-view background drawable cache. We use a single
     * `GradientDrawable` per view so a `backgroundColor` setStyle and
     * a `cornerRadius` setStyle both target the same drawable
     * (otherwise the later call would clobber the earlier one).
     */
    private val bgDrawables = mutableMapOf<Long, GradientDrawable>()

    private fun getOrCreateBgDrawable(handle: Long, view: View): GradientDrawable {
        bgDrawables[handle]?.let { return it }
        val gd = GradientDrawable()
        bgDrawables[handle] = gd
        view.background = gd
        return gd
    }

    /**
     * M-EVP-14 round 2: dispatch setStyle commands emitted by the Nim
     * leaves. The Nim-side `isonim_android/renderer` already maps
     * CSS-style props to Android-style names (`background-color` →
     * `backgroundColor`, `flex-direction` → `orientation`,
     * `font-size` → `textSize`, `color` → `textColor`,
     * `border-radius` → `cornerRadius`), so this handler keys on the
     * Android-style names. The value column carries pre-translated
     * values too (e.g. `HORIZONTAL` not `row`).
     */
    private fun applyStyle(handle: Long, view: View, prop: String, value: String) {
        when (prop) {
            "backgroundColor" -> {
                try {
                    val color = Color.parseColor(value)
                    // Round-3 fix: `MaterialButton` uses
                    // `backgroundTint` rather than the `background`
                    // drawable for its fill colour. Setting
                    // `view.background = gd` (as we do for plain
                    // Views) is silently overridden by the M3 button's
                    // tint, which is why the round-3 reviewer saw
                    // every styled button render as the M3 default
                    // indigo regardless of the colour we passed in.
                    if (view is MaterialButton) {
                        view.backgroundTintList =
                            android.content.res.ColorStateList.valueOf(color)
                        // Track the requested colour so a subsequent
                        // `cornerRadius` setStyle still has access to
                        // it via the cached drawable; MaterialButton
                        // ignores the GradientDrawable for fill, but
                        // we still consult `bgDrawables[handle]` for
                        // corner-radius bookkeeping below.
                        val bg = getOrCreateBgDrawable(handle, view)
                        bg.setColor(color)
                    } else {
                        val bg = getOrCreateBgDrawable(handle, view)
                        bg.setColor(color)
                    }
                } catch (_: Exception) {}
            }
            "textColor" -> {
                try {
                    val color = Color.parseColor(value)
                    if (view is TextView) view.setTextColor(color)
                } catch (_: Exception) {}
            }
            "cornerRadius" -> {
                val v = value.toFloatOrNull() ?: return
                if (view is MaterialButton) {
                    // M3 buttons expose a first-class corner-radius
                    // API; the GradientDrawable path is a no-op for
                    // them.
                    view.cornerRadius = dp(v.toInt())
                } else {
                    val bg = getOrCreateBgDrawable(handle, view)
                    bg.cornerRadius = dp(v.toInt()).toFloat()
                }
            }
            "textSize" -> {
                // M-EVP-14 Wave-Q fix: treat the leaf-emitted
                // `font-size` value as a *design pixel* (sp / dp)
                // quantity, not a raw framebuffer pixel. Wave-P-B
                // experimentally swapped this to `COMPLEX_UNIT_PX`
                // to fix the task-app's oversized type, but on a
                // high-density device (Pixel 6 ≈ 2.75×) the same
                // change made settings-app type too small — at 1:1
                // crop the 14 px text rendered as ~9 sp equivalent.
                //
                // The right unit on Android is `COMPLEX_UNIT_SP`
                // (or `COMPLEX_UNIT_DIP`). SP is preferred for text
                // because it also honors the user's accessibility
                // font-scale setting. Now a leaf `font-size: 14`
                // means "14 sp" which on a 2.75× device renders at
                // ~38 framebuffer px — matching the original SP
                // path and the Material 3 type scale.
                //
                // Other backends (Cocoa, Freya, GPUI, Web) interpret
                // `font-size: 14` against their own coordinate
                // systems; on Android the design-pixel coordinate
                // system *is* sp / dp by convention, so this is the
                // direct port.
                val sp = value.toFloatOrNull() ?: return
                if (view is TextView) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            }
            "orientation" -> {
                val orient = if (value == "HORIZONTAL")
                    LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                if (view is LinearLayout) view.orientation = orient
            }
            "height" -> {
                val px = value.toIntOrNull()?.let { dp(it) } ?: return
                val lp = view.layoutParams ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.height = px
                view.layoutParams = lp
                if (view is TextView) view.gravity = Gravity.CENTER
            }
            "width" -> {
                val px = value.toIntOrNull()?.let { dp(it) } ?: return
                val lp = view.layoutParams ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.width = px
                view.layoutParams = lp
            }
            "padding" -> {
                val px = value.toIntOrNull()?.let { dp(it) } ?: return
                view.setPadding(px, px, px, px)
            }
            "gap" -> {
                gapValues[handle] = value.toIntOrNull() ?: return
                // Re-apply margins to already-attached children if any.
                if (view is ViewGroup) {
                    val gapPx = dp(gapValues[handle]!!)
                    val isHorizontal = view is LinearLayout &&
                        view.orientation == LinearLayout.HORIZONTAL
                    for (idx in 1 until view.childCount) {
                        val child = view.getChildAt(idx)
                        val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
                            ?: continue
                        if (isHorizontal) lp.leftMargin = gapPx
                        else lp.topMargin = gapPx
                        child.layoutParams = lp
                    }
                }
            }
            "flex-grow" -> {
                // Defer to appendChild: the child may not yet be
                // attached to its parent when `setStyle "flex-grow"`
                // arrives (the leaves call `setStyle` before
                // `appendChild`). We record the weight and apply it
                // in the appendChild handler once we know the parent
                // orientation. If the view is already attached, also
                // re-apply now so reactive updates work.
                val weight = value.toFloatOrNull() ?: return
                flexGrowWeights[handle] = weight
                applyFlexGrow(view, weight)
            }
            "elevation" -> {
                // Round-4 fix: settings_app group surface cards request
                // a Material 3 elevation hint via `setStyle("elevation",
                // "4")`.  Android's `View.elevation` API takes pixels;
                // we convert from the dp value the leaf emits.
                val dpVal = value.toIntOrNull() ?: return
                view.elevation = dp(dpVal).toFloat()
            }
        }
    }

    /** Per-view gap (dp) — applied as margin between siblings when laid out. */
    private val gapValues = mutableMapOf<Long, Int>()

    /**
     * Per-view flex-grow weight, recorded by `setStyle "flex-grow"` so
     * `appendChild` can promote the child's layoutParams to
     * `LinearLayout.LayoutParams` with the right `weight` + a zeroed
     * main-axis dimension. We need this two-phase because the leaves
     * emit `setStyle` *before* `appendChild`, so the parent's
     * orientation isn't known yet when the weight is first seen.
     */
    private val flexGrowWeights = mutableMapOf<Long, Float>()

    private fun applyFlexGrow(view: View, weight: Float) {
        val parent = view.parent as? LinearLayout ?: return
        val existing = view.layoutParams
        val isHorizontal = parent.orientation == LinearLayout.HORIZONTAL
        val newLp = if (existing is LinearLayout.LayoutParams) {
            existing.weight = weight
            if (isHorizontal) existing.width = 0
            else existing.height = 0
            existing
        } else {
            val width = if (isHorizontal) 0
                else (existing?.width ?: ViewGroup.LayoutParams.MATCH_PARENT)
            val height = if (isHorizontal)
                (existing?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT)
                else 0
            LinearLayout.LayoutParams(width, height, weight).also { lp ->
                if (existing is ViewGroup.MarginLayoutParams) {
                    lp.setMargins(existing.leftMargin, existing.topMargin,
                                   existing.rightMargin, existing.bottomMargin)
                }
            }
        }
        view.layoutParams = newLp
    }

    companion object {
        private val SUPPRESS_WATCHER = Any()
        private val WATCHER_INSTALLED = Any()
        // M-EVP-14 Wave-Q fix: default text size for any text-bearing
        // view the leaves don't size explicitly. Density-aware `sp`
        // (M3 bodyLarge baseline) — see the `textSize` setStyle
        // dispatch in `applyStyle` for the unit-chain rationale.
        // On a Pixel 6 (density ~2.75) this is ~44 framebuffer px,
        // which lands inside the M3 type scale's body-large band.
        private const val DEFAULT_TEXT_SP = 16f
    }
}
