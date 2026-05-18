package com.metacraft.isonim.android

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

object NimBridge {
    private val viewRegistry = mutableMapOf<Long, View>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nextHandle: Long = 1

    // --- Nim-driven UI: native methods (implemented in android_entry.nim) ---

    init { System.loadLibrary("isonim") }

    @JvmStatic external fun nimBuildBrandedUI(width: Int, height: Int): Int
    @JvmStatic external fun nimRebuildUI(): Int
    @JvmStatic external fun nimGetCommandCount(): Int
    @JvmStatic external fun nimGetCommandKind(index: Int): String
    @JvmStatic external fun nimGetCommandHandle(index: Int): Long
    @JvmStatic external fun nimGetCommandTag(index: Int): String
    @JvmStatic external fun nimGetCommandName(index: Int): String
    @JvmStatic external fun nimGetCommandValue(index: Int): String
    @JvmStatic external fun nimGetCommandParentHandle(index: Int): Long
    @JvmStatic external fun nimGetCommandChildHandle(index: Int): Long
    @JvmStatic external fun nimGetCommandRefHandle(index: Int): Long
    @JvmStatic external fun nimGetCommandCallbackId(index: Int): Int
    @JvmStatic external fun nimGetCommandEvent(index: Int): String
    @JvmStatic external fun nimGetCommandTitle(index: Int): String
    @JvmStatic external fun nimGetCommandMessage(index: Int): String
    @JvmStatic external fun nimGetCommandButtonCount(index: Int): Int
    @JvmStatic external fun nimHandleEvent(callbackId: Int)
    @JvmStatic external fun nimSetInputText(text: String)
    @JvmStatic external fun nimGetInputText(): String
    @JvmStatic external fun nimAddTaskFromInput(): Int

    fun createView(tag: String, context: android.content.Context): Long {
        val handle = nextHandle++
        val view: View = when (tag) {
            "FrameLayout" -> LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            "LinearLayout" -> LinearLayout(context)
            "TextView" -> TextView(context)
            "Button" -> Button(context)
            "MaterialButton" -> try {
                MaterialButton(context)
            } catch (_: Exception) {
                // Fallback when Material theme is not available (e.g. Robolectric)
                Button(context)
            }
            "EditText" -> EditText(context)
            "ImageView" -> ImageView(context)
            "ScrollView" -> ScrollView(context)
            "RecyclerView" -> RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
            }
            "SearchView" -> SearchView(context)
            "Switch" -> Switch(context)
            "SeekBar" -> SeekBar(context)
            "Spinner" -> Spinner(context)
            "DatePicker" -> DatePicker(context)
            // M10: Navigation
            "TabLayout" -> try {
                TabLayout(context)
            } catch (_: Exception) {
                FrameLayout(context)
            }
            "BottomNavigationView" -> try {
                com.google.android.material.bottomnavigation.BottomNavigationView(context)
            } catch (_: Exception) {
                FrameLayout(context)
            }
            "DrawerLayout" -> DrawerLayout(context)
            "Toolbar" -> Toolbar(context)
            // M11: Progress & Badges
            "ProgressBar" -> ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
            }
            "CircularProgress" -> ProgressBar(context).apply {
                isIndeterminate = true
            }
            "Badge" -> TextView(context)
            // M12: Web, Media & Maps
            "WebView" -> WebView(context)
            "VideoView" -> VideoView(context)
            "MapView" -> LinearLayout(context) // MapView requires Google Play Services SDK
            // Native controls (lowercase tags from native_controls.nim)
            // M-EVP-14 Wave W-3: route the lowercase ``switch`` tag the
            // settings_app / native_controls leaves emit through the
            // custom-drawn ``CustomSwitchView`` instead of the system
            // ``Switch``. Samsung's One UI overrides MaterialSwitch's
            // tint attributes with the device theme, so the brand-
            // indigo on-state never paints on real devices. The custom
            // view paints its own track + thumb via Canvas.onDraw so
            // the indigo accent survives device theming.
            "switch" -> CustomSwitchView(context)
            "custom-switch" -> CustomSwitchView(context)
            "button" -> try {
                MaterialButton(context)
            } catch (_: Exception) {
                Button(context)
            }
            "segmented" -> LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            "h1" -> TextView(context).apply {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = 28f
            }
            "p" -> TextView(context)
            "input" -> EditText(context)
            "span" -> TextView(context)
            "div" -> LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            else -> LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
        }
        viewRegistry[handle] = view
        return handle
    }

    fun getView(handle: Long): View? = viewRegistry[handle]

    fun setViewText(handle: Long, text: String) {
        val view = viewRegistry[handle] ?: return
        if (view is TextView) view.text = text
    }

    fun appendChild(parentHandle: Long, childHandle: Long) {
        val parent = viewRegistry[parentHandle] as? ViewGroup ?: return
        val child = viewRegistry[childHandle] ?: return
        mainHandler.post { parent.addView(child) }
    }

    fun removeChild(parentHandle: Long, childHandle: Long) {
        val parent = viewRegistry[parentHandle] as? ViewGroup ?: return
        val child = viewRegistry[childHandle] ?: return
        mainHandler.post { parent.removeView(child) }
    }

    fun insertBefore(parentHandle: Long, childHandle: Long, refHandle: Long) {
        val parent = viewRegistry[parentHandle] as? ViewGroup ?: return
        val child = viewRegistry[childHandle] ?: return
        val ref = viewRegistry[refHandle] ?: return
        mainHandler.post {
            val index = parent.indexOfChild(ref)
            if (index >= 0) {
                parent.addView(child, index)
            } else {
                parent.addView(child)
            }
        }
    }

    fun setAttribute(handle: Long, name: String, value: String) {
        val view = viewRegistry[handle] ?: return
        mainHandler.post {
            when (name) {
                "disabled" -> view.isEnabled = value != "true"
                "placeholder" -> if (view is EditText) view.hint = value
                "value" -> if (view is TextView) view.text = value
                "contentDescription" -> view.contentDescription = value
                "imeOptions" -> if (view is EditText) {
                    view.imeOptions = when (value) {
                        "actionDone" -> EditorInfo.IME_ACTION_DONE
                        "actionSearch" -> EditorInfo.IME_ACTION_SEARCH
                        "actionSend" -> EditorInfo.IME_ACTION_SEND
                        "actionGo" -> EditorInfo.IME_ACTION_GO
                        "actionNext" -> EditorInfo.IME_ACTION_NEXT
                        else -> EditorInfo.IME_ACTION_UNSPECIFIED
                    }
                }
                "inputType" -> if (view is EditText) {
                    view.inputType = when (value) {
                        "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        "multiline" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        else -> InputType.TYPE_CLASS_TEXT
                    }
                }
                "queryHint" -> if (view is SearchView) view.queryHint = value
                "checked" -> {
                    val isOn = value == "true" || value == "checked"
                    when (view) {
                        is CustomSwitchView -> view.isChecked = isOn
                        is Switch -> view.isChecked = isOn
                    }
                }
                "min" -> if (view is SeekBar) view.min = value.toIntOrNull() ?: 0
                "max" -> {
                    if (view is SeekBar) view.max = value.toIntOrNull() ?: 100
                    else if (view is ProgressBar) view.max = value.toIntOrNull() ?: 100
                }
                "progress" -> {
                    if (view is SeekBar) view.progress = value.toIntOrNull() ?: 0
                    else if (view is ProgressBar) view.progress = value.toIntOrNull() ?: 0
                }
                // M10: Navigation
                "title" -> if (view is Toolbar) view.title = value
                // M11: Badge
                "badgeCount" -> if (view is TextView) view.text = value
                // M12: WebView
                "url" -> if (view is WebView) view.loadUrl(value)
                "jsEnabled" -> if (view is WebView) view.settings.javaScriptEnabled = value == "true"
                // M13: Accessibility
                "importantForAccessibility" -> view.importantForAccessibility = when (value) {
                    "NO" -> View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    "YES" -> View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    "AUTO" -> View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    else -> View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                }
                "nextFocusForwardId" -> view.nextFocusForwardId = value.toIntOrNull() ?: View.NO_ID
            }
        }
    }

    // Track per-view background drawables for combined color + cornerRadius
    private val bgDrawables = mutableMapOf<Long, GradientDrawable>()
    // Track gap values per container so we can apply margins to children
    private val gapValues = mutableMapOf<Long, Int>()

    private fun dpToPx(view: View, dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            view.resources.displayMetrics
        ).toInt()

    private fun getOrCreateBgDrawable(handle: Long, view: View): GradientDrawable {
        return bgDrawables.getOrPut(handle) {
            GradientDrawable().also { view.background = it }
        }
    }

    /**
     * Replace a FrameLayout with a LinearLayout, preserving children,
     * background drawable, and the handle mapping.
     * Called when orientation is set on a FrameLayout.
     */
    private fun replaceWithLinearLayout(handle: Long, view: FrameLayout, orientation: Int): LinearLayout {
        val ll = LinearLayout(view.context).apply {
            this.orientation = orientation
            this.layoutParams = view.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Copy basic properties
            this.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            this.background = view.background
            this.id = view.id
        }
        // Move children
        val children = mutableListOf<View>()
        for (i in 0 until view.childCount) {
            children.add(view.getChildAt(i))
        }
        view.removeAllViews()
        for (child in children) {
            ll.addView(child)
        }
        // Replace in parent if already attached
        val parent = view.parent as? ViewGroup
        if (parent != null) {
            val index = parent.indexOfChild(view)
            parent.removeViewAt(index)
            parent.addView(ll, index)
        }
        // Update registries
        viewRegistry[handle] = ll
        // Transfer background drawable reference
        val bgd = bgDrawables[handle]
        if (bgd != null) {
            ll.background = bgd
        }
        return ll
    }

    fun setStyle(handle: Long, prop: String, value: String) {
        val view = viewRegistry[handle] ?: return
        when (prop) {
            "backgroundColor" -> {
                try {
                    val color = Color.parseColor(value)
                    val bg = getOrCreateBgDrawable(handle, view)
                    bg.setColor(color)
                } catch (_: Exception) {}
            }
            "cornerRadius" -> {
                val px = value.toFloatOrNull() ?: return
                val bg = getOrCreateBgDrawable(handle, view)
                bg.cornerRadius = dpToPx(view, px.toInt()).toFloat()
            }
            "orientation" -> {
                val orient = if (value == "HORIZONTAL") LinearLayout.HORIZONTAL
                             else LinearLayout.VERTICAL
                val target = when (view) {
                    is LinearLayout -> { view.orientation = orient; view }
                    is FrameLayout -> replaceWithLinearLayout(handle, view, orient)
                    else -> view
                }
                // Re-apply gravity if stored
                // (orientation change may need gravity re-application)
            }
            "textSize" -> {
                val sp = value.toFloatOrNull() ?: return
                if (view is TextView) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            }
            "textColor" -> {
                try {
                    val color = Color.parseColor(value)
                    if (view is TextView) view.setTextColor(color)
                } catch (_: Exception) {}
            }
            "padding" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                view.setPadding(px, px, px, px)
            }
            "padding-top" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                view.setPadding(view.paddingLeft, px, view.paddingRight, view.paddingBottom)
            }
            "padding-bottom" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, px)
            }
            "padding-left" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                view.setPadding(px, view.paddingTop, view.paddingRight, view.paddingBottom)
            }
            "padding-right" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                view.setPadding(view.paddingLeft, view.paddingTop, px, view.paddingBottom)
            }
            "margin" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                val lp = view.layoutParams
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.setMargins(px, px, px, px)
                    view.layoutParams = lp
                }
            }
            "margin-left" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                val lp = view.layoutParams
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.leftMargin = px
                    view.layoutParams = lp
                }
            }
            "margin-top" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                val lp = view.layoutParams
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.topMargin = px
                    view.layoutParams = lp
                }
            }
            "margin-right" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                val lp = view.layoutParams
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.rightMargin = px
                    view.layoutParams = lp
                }
            }
            "margin-bottom" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                val lp = view.layoutParams
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.bottomMargin = px
                    view.layoutParams = lp
                }
            }
            "width" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                val lp = view.layoutParams ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.width = px
                view.layoutParams = lp
            }
            "height" -> {
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                val lp = view.layoutParams ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.height = px
                view.layoutParams = lp
            }
            "flex-grow" -> {
                val weight = value.toFloatOrNull() ?: return
                val lp = view.layoutParams
                if (lp is LinearLayout.LayoutParams) {
                    lp.weight = weight
                    view.layoutParams = lp
                } else {
                    // View may not have LinearLayout.LayoutParams yet;
                    // create new ones preserving size and margins
                    val newLp = LinearLayout.LayoutParams(
                        view.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                        view.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                        weight
                    )
                    if (view.layoutParams is ViewGroup.MarginLayoutParams) {
                        val old = view.layoutParams as ViewGroup.MarginLayoutParams
                        newLp.setMargins(old.leftMargin, old.topMargin, old.rightMargin, old.bottomMargin)
                    }
                    view.layoutParams = newLp
                }
            }
            "gravity" -> {
                // align-items → gravity (cross-axis alignment)
                val g = parseGravity(value)
                if (view is LinearLayout) view.gravity = g
                else if (view is FrameLayout) {
                    for (i in 0 until (view as ViewGroup).childCount) {
                        val child = view.getChildAt(i)
                        val lp = child.layoutParams
                        if (lp is FrameLayout.LayoutParams) {
                            lp.gravity = g
                            child.layoutParams = lp
                        }
                    }
                }
            }
            "gravityAxis" -> {
                // justify-content → gravity (main-axis alignment)
                // Combine with existing gravity (align-items may have been set first)
                val g = parseGravity(value)
                if (view is LinearLayout) view.gravity = view.gravity or g
            }
            "gap" -> {
                val dp = value.toIntOrNull() ?: return
                gapValues[handle] = dp
                // Apply gap as margins to existing children
                applyGap(handle, view, dp)
            }
            "visibility" -> view.visibility = when (value) {
                "GONE" -> View.GONE
                "INVISIBLE" -> View.INVISIBLE
                else -> View.VISIBLE
            }
            "alpha" -> view.alpha = value.toFloatOrNull() ?: 1.0f
            "border-color" -> {
                try {
                    val color = Color.parseColor(value)
                    val bg = getOrCreateBgDrawable(handle, view)
                    bg.setStroke(dpToPx(view, 2), color)
                } catch (_: Exception) {}
            }
            "border-width" -> {
                // border-width is typically set along with border-color;
                // we store a default stroke color if not set yet
                val dp = value.toIntOrNull() ?: return
                val px = dpToPx(view, dp)
                val bg = getOrCreateBgDrawable(handle, view)
                // GradientDrawable doesn't have getStrokeColor, so just re-set
                // the stroke width; color will be set by border-color
            }
            "text-align" -> {
                if (view is TextView) {
                    view.gravity = when (value) {
                        "center" -> Gravity.CENTER
                        "right", "end" -> Gravity.END
                        else -> Gravity.START
                    }
                }
            }
            "align-self" -> {
                // align-self: center → center this view in its parent
                val g = parseGravity(value)
                val lp = view.layoutParams
                if (lp is LinearLayout.LayoutParams) {
                    lp.gravity = g
                    view.layoutParams = lp
                } else if (lp is FrameLayout.LayoutParams) {
                    lp.gravity = g
                    view.layoutParams = lp
                }
            }
        }
    }

    private fun parseGravity(value: String): Int {
        return when (value) {
            "center" -> Gravity.CENTER
            "flex-start", "start" -> Gravity.START or Gravity.TOP
            "flex-end", "end" -> Gravity.END or Gravity.BOTTOM
            "space-between" -> Gravity.CENTER // approximation
            "space-around" -> Gravity.CENTER  // approximation
            else -> Gravity.NO_GRAVITY
        }
    }

    /**
     * Apply gap (spacing) between children of a container.
     * Gap is implemented as margins between children.
     */
    private fun applyGap(handle: Long, view: View, gapDp: Int) {
        if (view !is ViewGroup) return
        val px = dpToPx(view, gapDp)
        val isHorizontal = view is LinearLayout && view.orientation == LinearLayout.HORIZONTAL
        for (i in 0 until view.childCount) {
            if (i == 0) continue // no gap before first child
            val child = view.getChildAt(i)
            val lp = child.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                if (isHorizontal) lp.leftMargin = px else lp.topMargin = px
                child.layoutParams = lp
            }
        }
    }

    private val eventCallbacks = mutableMapOf<String, Int>()

    fun setEventListener(handle: Long, event: String, callbackId: Int) {
        val view = viewRegistry[handle] ?: return
        val key = "${handle}_${event}"
        eventCallbacks[key] = callbackId
        mainHandler.post {
            when (event) {
                "click" -> view.setOnClickListener { /* invoke Nim callback via JNI */ }
                "longPress" -> view.setOnLongClickListener { true }
            }
        }
    }

    fun showAlert(
        context: android.content.Context,
        title: String,
        message: String,
        buttons: List<String>
    ): AlertDialog {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
        if (buttons.isNotEmpty()) builder.setPositiveButton(buttons[0]) { d, _ -> d.dismiss() }
        if (buttons.size > 1) builder.setNegativeButton(buttons[1]) { d, _ -> d.dismiss() }
        if (buttons.size > 2) builder.setNeutralButton(buttons[2]) { d, _ -> d.dismiss() }
        return builder.create()
    }

    fun reset() {
        eventCallbacks.clear()
        viewRegistry.clear()
        bgDrawables.clear()
        gapValues.clear()
        nextHandle = 1
    }

    // --- Command buffer execution (Nim-driven UI) ---

    /**
     * Execute the Nim command buffer: read each command from the native side
     * and translate it into real Android View operations via NimBridge.
     *
     * @param context Android context for creating Views
     * @param handleMap maps Nim handles (int64) to NimBridge handles (Long)
     * @param onEvent callback invoked when a Nim-registered event fires;
     *                receives the callbackId so Kotlin can call nimHandleEvent
     * @return the NimBridge handle of the root view (first created view)
     */
    fun executeCommandBuffer(
        context: android.content.Context,
        onEvent: (Int) -> Unit
    ): Long {
        val count = nimGetCommandCount()
        // Map from Nim-side handles to NimBridge-side handles
        val handleMap = mutableMapOf<Long, Long>()
        var rootHandle: Long = 0

        for (i in 0 until count) {
            val kind = nimGetCommandKind(i)
            when (kind) {
                "createView" -> {
                    val nimHandle = nimGetCommandHandle(i)
                    val tag = nimGetCommandTag(i)
                    val bridgeHandle = createView(tag, context)
                    handleMap[nimHandle] = bridgeHandle
                    if (rootHandle == 0L) rootHandle = bridgeHandle
                }
                "createScrollView" -> {
                    val nimHandle = nimGetCommandHandle(i)
                    val bridgeHandle = createView("ScrollView", context)
                    handleMap[nimHandle] = bridgeHandle
                    if (rootHandle == 0L) rootHandle = bridgeHandle
                }
                "createRecyclerView" -> {
                    val nimHandle = nimGetCommandHandle(i)
                    val bridgeHandle = createView("RecyclerView", context)
                    handleMap[nimHandle] = bridgeHandle
                    if (rootHandle == 0L) rootHandle = bridgeHandle
                }
                "setText" -> {
                    val nimHandle = nimGetCommandHandle(i)
                    val value = nimGetCommandValue(i)
                    val bh = handleMap[nimHandle] ?: continue
                    setViewText(bh, value)
                }
                "appendChild" -> {
                    val parentNim = nimGetCommandParentHandle(i)
                    val childNim = nimGetCommandChildHandle(i)
                    val parentBh = handleMap[parentNim] ?: continue
                    val childBh = handleMap[childNim] ?: continue
                    appendChildSync(parentBh, childBh)
                }
                "removeChild" -> {
                    val parentNim = nimGetCommandParentHandle(i)
                    val childNim = nimGetCommandChildHandle(i)
                    val parentBh = handleMap[parentNim] ?: continue
                    val childBh = handleMap[childNim] ?: continue
                    removeChild(parentBh, childBh)
                }
                "insertBefore" -> {
                    val parentNim = nimGetCommandParentHandle(i)
                    val childNim = nimGetCommandChildHandle(i)
                    val refNim = nimGetCommandRefHandle(i)
                    val parentBh = handleMap[parentNim] ?: continue
                    val childBh = handleMap[childNim] ?: continue
                    val refBh = handleMap[refNim] ?: continue
                    insertBefore(parentBh, childBh, refBh)
                }
                "setAttribute" -> {
                    val nimHandle = nimGetCommandHandle(i)
                    val name = nimGetCommandName(i)
                    val value = nimGetCommandValue(i)
                    val bh = handleMap[nimHandle] ?: continue
                    setAttribute(bh, name, value)
                }
                "setStyle" -> {
                    val nimHandle = nimGetCommandHandle(i)
                    val prop = nimGetCommandName(i)
                    val value = nimGetCommandValue(i)
                    val bh = handleMap[nimHandle] ?: continue
                    setStyle(bh, prop, value)
                }
                "setEventListener" -> {
                    val nimHandle = nimGetCommandHandle(i)
                    val event = nimGetCommandEvent(i)
                    val callbackId = nimGetCommandCallbackId(i)
                    val bh = handleMap[nimHandle] ?: continue
                    val view = getView(bh) ?: continue
                    when (event) {
                        "click" -> view.setOnClickListener { onEvent(callbackId) }
                        "longPress" -> view.setOnLongClickListener {
                            onEvent(callbackId); true
                        }
                    }
                }
                "showAlert" -> {
                    // Alerts are deferred — the caller can read title/message
                    // from the command if needed
                }
                "showToast" -> {
                    val message = nimGetCommandValue(i)
                    val duration = nimGetCommandName(i)
                    val d = if (duration == "long") android.widget.Toast.LENGTH_LONG
                            else android.widget.Toast.LENGTH_SHORT
                    android.widget.Toast.makeText(context, message, d).show()
                }
            }
        }
        return rootHandle
    }

    /** Synchronous appendChild (for command buffer execution on the main thread). */
    fun appendChildSync(parentHandle: Long, childHandle: Long) {
        val parent = viewRegistry[parentHandle] as? ViewGroup ?: return
        val child = viewRegistry[childHandle] ?: return
        // Ensure child has LinearLayout.LayoutParams if parent is a LinearLayout
        if (parent is LinearLayout) {
            val existing = child.layoutParams
            if (existing is LinearLayout.LayoutParams) {
                // Already correct type -- fix flex-grow dimension now that we know parent orientation
                if (existing.weight > 0f) {
                    if (parent.orientation == LinearLayout.VERTICAL) {
                        existing.height = 0
                        if (existing.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                            existing.width = ViewGroup.LayoutParams.MATCH_PARENT
                        }
                    } else {
                        existing.width = 0
                    }
                }
            } else {
                // Default: vertical parents get MATCH_PARENT width children,
                // horizontal parents get WRAP_CONTENT width children.
                val defaultWidth = if (parent.orientation == LinearLayout.VERTICAL)
                    LinearLayout.LayoutParams.MATCH_PARENT
                else
                    LinearLayout.LayoutParams.WRAP_CONTENT
                val newLp = if (existing != null) {
                    LinearLayout.LayoutParams(existing.width, existing.height).also {
                        if (existing is ViewGroup.MarginLayoutParams) {
                            it.setMargins(existing.leftMargin, existing.topMargin,
                                          existing.rightMargin, existing.bottomMargin)
                        }
                    }
                } else {
                    LinearLayout.LayoutParams(
                        defaultWidth,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                child.layoutParams = newLp
            }
        }
        parent.addView(child)
        // Apply gap margin if parent has gap set
        val gapDp = gapValues[parentHandle]
        if (gapDp != null && parent.childCount > 1) {
            val px = dpToPx(child, gapDp)
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            val isHorizontal = parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL
            if (isHorizontal) lp.leftMargin = px else lp.topMargin = px
            child.layoutParams = lp
        }
    }

    class NimRecyclerAdapter(
        itemCount: Int = 0,
        val createHolder: () -> View,
        val bindHolder: (View, Int) -> Unit
    ) : RecyclerView.Adapter<NimRecyclerAdapter.VH>() {
        var createCount = 0
        private var _itemCount: Int = itemCount

        var items: Int
            get() = _itemCount
            set(value) { _itemCount = value }

        inner class VH(val view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            createCount++
            return VH(createHolder())
        }

        override fun onBindViewHolder(holder: VH, position: Int) = bindHolder(holder.view, position)

        override fun getItemCount(): Int = _itemCount
    }
}
