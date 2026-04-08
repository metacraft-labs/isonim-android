package com.metacraft.isonim.android

import android.view.View
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class AccessibilityTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun contentDescriptionSetGet() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("FrameLayout", ctx)
        NimBridge.setAttribute(handle, "contentDescription", "Main container")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle)!!
        assertEquals("Main container", view.contentDescription)
    }

    @Test
    fun importantForAccessibility() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("FrameLayout", ctx)
        NimBridge.setAttribute(handle, "importantForAccessibility", "NO")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle)!!
        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO,
            view.importantForAccessibility
        )
    }

    @Test
    fun focusChainNextFocusForwardId() {
        val ctx = RuntimeEnvironment.getApplication()
        val h1 = NimBridge.createView("FrameLayout", ctx)
        val h2 = NimBridge.createView("FrameLayout", ctx)
        val view2 = NimBridge.getView(h2)!!
        val targetId = view2.id.let { if (it == View.NO_ID) { view2.id = View.generateViewId(); view2.id } else it }
        NimBridge.setAttribute(h1, "nextFocusForwardId", targetId.toString())
        ShadowLooper.idleMainLooper()
        val view1 = NimBridge.getView(h1)!!
        assertEquals(targetId, view1.nextFocusForwardId)
    }
}
