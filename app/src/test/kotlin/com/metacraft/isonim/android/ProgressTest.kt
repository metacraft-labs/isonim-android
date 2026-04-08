package com.metacraft.isonim.android

import android.view.View
import android.widget.ProgressBar
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class ProgressTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun progressBarSetAndVerify() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("ProgressBar", ctx)
        NimBridge.setAttribute(handle, "progress", "75")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle) as ProgressBar
        assertEquals(75, view.progress)
    }

    @Test
    fun circularProgressVisibility() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("CircularProgress", ctx)
        val view = NimBridge.getView(handle)
        assertNotNull("CircularProgress should be created", view)
        // CircularProgress is a ProgressBar with indeterminate style
        assertTrue(view is ProgressBar)
        NimBridge.setStyle(handle, "visibility", "GONE")
        ShadowLooper.idleMainLooper()
        assertEquals(View.GONE, view!!.visibility)
    }

    @Test
    fun badgeCount() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("Badge", ctx)
        val view = NimBridge.getView(handle)
        assertNotNull("Badge view should be created", view)
        NimBridge.setAttribute(handle, "badgeCount", "3")
        ShadowLooper.idleMainLooper()
        // Badge is backed by a TextView showing the count
        if (view is android.widget.TextView) {
            assertEquals("3", view.text.toString())
        }
    }
}
