package com.metacraft.isonim.android

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

object NimBridge {
    private val viewRegistry = mutableMapOf<Long, View>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nextHandle: Long = 1

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
                "max" -> if (view is SeekBar) view.max = value.toIntOrNull() ?: 100
                "progress" -> if (view is SeekBar) view.progress = value.toIntOrNull() ?: 0
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
