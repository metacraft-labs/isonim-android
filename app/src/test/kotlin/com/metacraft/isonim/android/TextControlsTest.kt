package com.metacraft.isonim.android

import android.text.InputType
import android.widget.EditText
import android.widget.SearchView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class TextControlsTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun editTextMultilineFlag() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("EditText", ctx)
        NimBridge.setAttribute(handle, "inputType", "multiline")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle) as EditText
        assertTrue(
            "inputType should have multiline flag",
            (view.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        )
        // Verify multiline text can be set (newlines preserved)
        view.setText("line1\nline2\nline3")
        assertTrue("Text should contain newlines", view.text.contains("\n"))
    }

    @Test
    fun passwordInputType() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("EditText", ctx)
        NimBridge.setAttribute(handle, "inputType", "password")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle) as EditText
        assertTrue(
            "inputType should have password flag",
            (view.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0
        )
    }

    @Test
    fun searchViewCreation() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("SearchView", ctx)
        val view = NimBridge.getView(handle)
        assertNotNull(view)
        assertTrue("Should be a SearchView", view is SearchView)
    }

    @Test
    fun searchViewQueryHint() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("SearchView", ctx)
        NimBridge.setAttribute(handle, "queryHint", "Search here...")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle) as SearchView
        assertEquals("Search here...", view.queryHint.toString())
    }
}
