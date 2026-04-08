package com.metacraft.isonim.android

import android.content.res.Configuration
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class MaterialTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun materialButtonCreationFallsBackToButton() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("MaterialButton", ctx)
        val view = NimBridge.getView(handle)
        assertNotNull(view)
        // In Robolectric, MaterialButton may not be available, so it falls back to Button
        assertTrue("Should be a Button instance", view is Button)
    }

    @Test
    fun darkModeDoesNotCrash() {
        val ctx = RuntimeEnvironment.getApplication()
        val config = Configuration(ctx.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            Configuration.UI_MODE_NIGHT_YES
        val nightCtx = ctx.createConfigurationContext(config)
        // Creating views in dark mode should not crash
        val handle = NimBridge.createView("FrameLayout", nightCtx)
        val view = NimBridge.getView(handle)
        assertNotNull(view)
    }

    @Test
    fun imeActionSetOnEditText() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("EditText", ctx)
        NimBridge.setAttribute(handle, "imeOptions", "actionDone")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle) as EditText
        assertEquals(EditorInfo.IME_ACTION_DONE, view.imeOptions)
    }
}
