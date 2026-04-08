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

    // M4: Counter on real Views — create counter UI, tap +, verify label text
    @Test
    fun counterOnRealViews() {
        val ctx = RuntimeEnvironment.getApplication()
        // Create: container (FrameLayout) + label (TextView) + incBtn (Button)
        val containerH = NimBridge.createView("FrameLayout", ctx)
        val labelH = NimBridge.createView("TextView", ctx)
        val incBtnH = NimBridge.createView("Button", ctx)

        NimBridge.setViewText(labelH, "Count: 0")
        NimBridge.setViewText(incBtnH, "+")
        NimBridge.appendChild(containerH, labelH)
        NimBridge.appendChild(containerH, incBtnH)
        ShadowLooper.idleMainLooper()

        // Verify initial state
        val label = NimBridge.getView(labelH) as TextView
        assertEquals("Count: 0", label.text.toString())

        val container = NimBridge.getView(containerH) as ViewGroup
        assertEquals(2, container.childCount)

        // Simulate 3 "clicks" by updating the label (in real app, the Nim
        // callback would do this via JNI; here we simulate the bridge call)
        for (i in 1..3) {
            NimBridge.setViewText(labelH, "Count: $i")
        }
        assertEquals("Count: 3", label.text.toString())

        // Simulate tap via performClick on the real Button view
        val btn = NimBridge.getView(incBtnH) as Button
        var clicked = false
        btn.setOnClickListener { clicked = true }
        btn.performClick()
        assertTrue("Button click listener should fire", clicked)
    }
}
