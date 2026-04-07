package com.metacraft.isonim.android

import android.widget.*
import android.view.ViewGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Before
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class NimBridgeTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun createViewReturnsHandle() {
        val handle = NimBridge.createView("TextView", RuntimeEnvironment.getApplication())
        assertTrue(handle > 0)
    }

    @Test
    fun createViewStoresInRegistry() {
        val handle = NimBridge.createView("TextView", RuntimeEnvironment.getApplication())
        val view = NimBridge.getView(handle)
        assertNotNull(view)
        assertTrue(view is TextView)
    }

    @Test
    fun setViewTextUpdatesTextView() {
        val handle = NimBridge.createView("TextView", RuntimeEnvironment.getApplication())
        NimBridge.setViewText(handle, "hello")
        val tv = NimBridge.getView(handle) as TextView
        assertEquals("hello", tv.text.toString())
    }

    @Test
    fun appendChildAddsToParent() {
        val ctx = RuntimeEnvironment.getApplication()
        val parentH = NimBridge.createView("FrameLayout", ctx)
        val childH = NimBridge.createView("TextView", ctx)
        NimBridge.appendChild(parentH, childH)
        ShadowLooper.idleMainLooper()
        val parent = NimBridge.getView(parentH) as ViewGroup
        assertEquals(1, parent.childCount)
    }
}
