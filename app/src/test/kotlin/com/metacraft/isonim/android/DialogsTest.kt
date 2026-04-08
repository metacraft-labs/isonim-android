package com.metacraft.isonim.android

import android.app.AlertDialog
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class DialogsTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun alertDialogWithThreeButtons() {
        val ctx = RuntimeEnvironment.getApplication()
        val dialog = NimBridge.showAlert(ctx, "Title", "Message", listOf("OK", "Cancel", "Later"))
        dialog.show()
        ShadowLooper.idleMainLooper()

        val shadow = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("AlertDialog should be shown", shadow)
        assertTrue(shadow.isShowing)

        // Verify buttons exist
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE))
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_NEGATIVE))
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_NEUTRAL))
    }

    @Test
    fun alertDialogTitle() {
        val ctx = RuntimeEnvironment.getApplication()
        val dialog = NimBridge.showAlert(ctx, "Error", "Something failed", listOf("OK"))
        dialog.show()
        ShadowLooper.idleMainLooper()

        val shadow = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(shadow)
        // Positive button text
        assertEquals("OK", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
    }

    @Test
    fun alertDialogDismiss() {
        val ctx = RuntimeEnvironment.getApplication()
        val dialog = NimBridge.showAlert(ctx, "Info", "Note", listOf("OK"))
        dialog.show()
        ShadowLooper.idleMainLooper()
        assertTrue(dialog.isShowing)
        dialog.dismiss()
        ShadowLooper.idleMainLooper()
        assertFalse(dialog.isShowing)
    }
}
