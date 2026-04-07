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

    @Test
    fun removeChildRemovesFromParent() {
        val ctx = RuntimeEnvironment.getApplication()
        val parentH = NimBridge.createView("FrameLayout", ctx)
        val childH = NimBridge.createView("TextView", ctx)
        NimBridge.appendChild(parentH, childH)
        ShadowLooper.idleMainLooper()
        NimBridge.removeChild(parentH, childH)
        ShadowLooper.idleMainLooper()
        val parent = NimBridge.getView(parentH) as ViewGroup
        assertEquals(0, parent.childCount)
    }

    @Test
    fun insertBeforeInsertsAtCorrectPosition() {
        val ctx = RuntimeEnvironment.getApplication()
        val parentH = NimBridge.createView("FrameLayout", ctx)
        val child1H = NimBridge.createView("TextView", ctx)
        val child2H = NimBridge.createView("TextView", ctx)
        val child3H = NimBridge.createView("TextView", ctx)
        NimBridge.appendChild(parentH, child1H)
        NimBridge.appendChild(parentH, child3H)
        ShadowLooper.idleMainLooper()
        NimBridge.insertBefore(parentH, child2H, child3H)
        ShadowLooper.idleMainLooper()
        val parent = NimBridge.getView(parentH) as ViewGroup
        assertEquals(3, parent.childCount)
        assertEquals(NimBridge.getView(child1H), parent.getChildAt(0))
        assertEquals(NimBridge.getView(child2H), parent.getChildAt(1))
        assertEquals(NimBridge.getView(child3H), parent.getChildAt(2))
    }

    @Test
    fun setAttributeDisabledDisablesView() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("Button", ctx)
        NimBridge.setAttribute(handle, "disabled", "true")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle)!!
        assertFalse(view.isEnabled)
    }

    @Test
    fun setStyleBackgroundColorSetsBackground() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("FrameLayout", ctx)
        NimBridge.setStyle(handle, "backgroundColor", "#FFFF0000")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle)!!
        assertNotNull(view.background)
    }
}
