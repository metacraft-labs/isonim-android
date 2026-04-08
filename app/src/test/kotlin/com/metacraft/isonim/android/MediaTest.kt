package com.metacraft.isonim.android

import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowWebView

@RunWith(RobolectricTestRunner::class)
class MediaTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun webViewLoadUrl() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("WebView", ctx)
        val view = NimBridge.getView(handle) as WebView
        NimBridge.setAttribute(handle, "url", "https://example.com")
        ShadowLooper.idleMainLooper()
        val shadow = org.robolectric.Shadows.shadowOf(view)
        assertEquals("https://example.com", shadow.lastLoadedUrl)
    }

    @Test
    fun webViewJsEnabled() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("WebView", ctx)
        val view = NimBridge.getView(handle) as WebView
        NimBridge.setAttribute(handle, "jsEnabled", "true")
        ShadowLooper.idleMainLooper()
        assertTrue("JavaScript should be enabled", view.settings.javaScriptEnabled)
    }
}
