package com.metacraft.isonim.android

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.InputType
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
            "FrameLayout" -> FrameLayout(context)
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
            "MapView" -> FrameLayout(context) // MapView requires Google Play Services SDK
            else -> FrameLayout(context)
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
                "checked" -> if (view is Switch) view.isChecked = value == "true"
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

    fun setStyle(handle: Long, prop: String, value: String) {
        val view = viewRegistry[handle] ?: return
        mainHandler.post {
            when (prop) {
                "backgroundColor" -> {
                    try {
                        view.setBackgroundColor(android.graphics.Color.parseColor(value))
                    } catch (_: Exception) {}
                }
                "visibility" -> view.visibility = when (value) {
                    "GONE" -> android.view.View.GONE
                    "INVISIBLE" -> android.view.View.INVISIBLE
                    else -> android.view.View.VISIBLE
                }
                "alpha" -> view.alpha = value.toFloatOrNull() ?: 1.0f
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
        parent.addView(child)
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
