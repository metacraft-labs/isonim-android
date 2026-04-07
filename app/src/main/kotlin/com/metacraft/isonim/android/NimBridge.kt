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

    fun reset() {
        viewRegistry.clear()
        nextHandle = 1
    }
}
