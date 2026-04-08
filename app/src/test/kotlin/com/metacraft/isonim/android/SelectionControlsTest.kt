package com.metacraft.isonim.android

import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class SelectionControlsTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun switchCheckedState() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("Switch", ctx)
        val view = NimBridge.getView(handle) as Switch
        assertFalse("Switch should be unchecked initially", view.isChecked)
        NimBridge.setAttribute(handle, "checked", "true")
        ShadowLooper.idleMainLooper()
        assertTrue("Switch should be checked", view.isChecked)
    }

    @Test
    fun seekBarProgress() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("SeekBar", ctx)
        NimBridge.setAttribute(handle, "max", "200")
        NimBridge.setAttribute(handle, "progress", "75")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle) as SeekBar
        assertEquals(200, view.max)
        assertEquals(75, view.progress)
    }

    @Test
    fun spinnerSelection() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("Spinner", ctx)
        val spinner = NimBridge.getView(handle) as Spinner
        val items = listOf("Apple", "Banana", "Cherry")
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, items)
        spinner.adapter = adapter
        spinner.setSelection(1)
        assertEquals("Banana", spinner.selectedItem)
    }

    @Test
    fun switchListenerFires() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("Switch", ctx)
        val view = NimBridge.getView(handle) as Switch
        var changed = false
        view.setOnCheckedChangeListener { _, _ -> changed = true }
        view.isChecked = true
        assertTrue("Listener should have fired", changed)
    }

    @Test
    fun seekBarMinValue() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("SeekBar", ctx)
        NimBridge.setAttribute(handle, "min", "10")
        ShadowLooper.idleMainLooper()
        val view = NimBridge.getView(handle) as SeekBar
        assertEquals(10, view.min)
    }
}
