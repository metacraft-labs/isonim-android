package com.metacraft.isonim.examples

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EX-M6 acceptance test.
 *
 * Drives the canonical `task_app` scripted scenario through a real
 * Android Activity on a real device, hitting the Nim composition root
 * via the `TaskAppBridge` JNI namespace (which calls into
 * `libtask_app.so`, cross-compiled from
 * `isonim-examples/task_app/main_android.nim`). Mirrors the
 * GPUI / Freya / Cocoa end-to-end tests, but every assertion reads the
 * real Android view tree — no mocks, no in-process `MockJNI`, no
 * mock-bridge shims.
 *
 * Scripted scenario (matches EX-M3 / EX-M4 / EX-M5):
 *   1. Add three tasks: "buy milk", "wash car", "call mom".
 *   2. Toggle the first task.
 *   3. Switch filter to "Active" — only the two un-toggled tasks
 *      should remain visible.
 *   4. Switch filter to "Completed" — only the one toggled task
 *      should remain.
 *
 * Assertions are issued via Espresso `onView(withContentDescription(...))`
 * — the descriptors are set by `MainActivity.assignStableDescriptors`
 * after each command-buffer materialisation.
 */
@RunWith(AndroidJUnit4::class)
class TaskAppScenarioTest {

    @Test
    fun scriptedScenario_addThreeToggleOneFilterSwitches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)

            // ---------------- Step 1: add "buy milk" ----------------
            onView(withContentDescription("task_input"))
                .perform(clearText(), typeText("buy milk"), closeSoftKeyboard())
            onView(withContentDescription("add_button")).perform(click())

            onView(withContentDescription("task_label_0"))
                .check(matches(withText("buy milk")))
            onView(withContentDescription("task_row_0")).check(matches(isDisplayed()))

            // ---------------- Step 2: add "wash car" ----------------
            onView(withContentDescription("task_input"))
                .perform(clearText(), typeText("wash car"), closeSoftKeyboard())
            onView(withContentDescription("add_button")).perform(click())

            onView(withContentDescription("task_label_0")).check(matches(withText("buy milk")))
            onView(withContentDescription("task_label_1")).check(matches(withText("wash car")))
            onView(withContentDescription("task_row_0")).check(matches(isDisplayed()))
            onView(withContentDescription("task_row_1")).check(matches(isDisplayed()))

            // ---------------- Step 3: add "call mom" ----------------
            onView(withContentDescription("task_input"))
                .perform(clearText(), typeText("call mom"), closeSoftKeyboard())
            onView(withContentDescription("add_button")).perform(click())

            onView(withContentDescription("task_label_0")).check(matches(withText("buy milk")))
            onView(withContentDescription("task_label_1")).check(matches(withText("wash car")))
            onView(withContentDescription("task_label_2")).check(matches(withText("call mom")))

            // ---------------- Step 4: toggle row 0 ("buy milk") ----------------
            onView(withContentDescription("toggle_0")).perform(click())

            // After toggle, the leaves re-emit the label as
            // "<name> (done)" for completed tasks. Assert the
            // post-toggle marker text on the same row index in the
            // unfiltered (All) view.
            onView(withContentDescription("task_label_0"))
                .check(matches(withText("buy milk (done)")))
            // Toggle marker should now be "[x]".
            onView(withContentDescription("toggle_0"))
                .check(matches(withText("[x]")))

            // ---------------- Step 5: filter -> Active ----------------
            onView(withContentDescription("filter_active")).perform(click())

            // Two un-toggled tasks remain. They should be displayed
            // as "wash car" and "call mom" (the completed "buy milk"
            // is hidden). After re-render under Active filter, the
            // row indices restart at 0.
            onView(withContentDescription("task_label_0"))
                .check(matches(withText("wash car")))
            onView(withContentDescription("task_label_1"))
                .check(matches(withText("call mom")))
            // There should be no third row.
            onView(withContentDescription("task_row_2")).check(doesNotExist())

            // ---------------- Step 6: filter -> Completed ----------------
            onView(withContentDescription("filter_completed")).perform(click())

            // Only the one toggled task remains, displayed with the
            // " (done)" marker.
            onView(withContentDescription("task_label_0"))
                .check(matches(withText("buy milk (done)")))
            onView(withContentDescription("task_row_1")).check(doesNotExist())
        }
    }
}
