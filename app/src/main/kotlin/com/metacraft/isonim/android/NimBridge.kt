package com.metacraft.isonim.android

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*

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
            "Button", "MaterialButton" -> Button(context)
            "EditText" -> EditText(context)
            "ImageView" -> ImageView(context)
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

    fun reset() {
        eventCallbacks.clear()
        viewRegistry.clear()
        nextHandle = 1
    }
}
