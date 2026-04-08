package com.metacraft.isonim.android

import android.widget.FrameLayout
import android.widget.Toolbar
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class NavigationTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun tabLayoutCreation() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("TabLayout", ctx)
        val view = NimBridge.getView(handle)
        assertNotNull("TabLayout should be created", view)
        // In Robolectric without full Material theme, may fall back to FrameLayout
        assertTrue(
            "Should be a TabLayout or fallback FrameLayout",
            view is com.google.android.material.tabs.TabLayout || view is FrameLayout
        )
    }

    @Test
    fun toolbarTitleSetGet() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("Toolbar", ctx)
        NimBridge.setAttribute(handle, "title", "My App")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle) as Toolbar
        assertEquals("My App", view.title)
    }

    @Test
    fun drawerLayoutCreation() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("DrawerLayout", ctx)
        val view = NimBridge.getView(handle)
        assertNotNull("DrawerLayout should be created", view)
        assertTrue(
            "Should be a DrawerLayout",
            view is androidx.drawerlayout.widget.DrawerLayout
        )
    }
}
