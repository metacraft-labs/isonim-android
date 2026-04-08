package com.metacraft.isonim.android

import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Before
    fun setUp() {
        SchedulerState.reset()
    }

    @Test
    fun activityLaunchCreatesFrameLayoutRoot() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            assertNotNull(activity.rootView)
            assertTrue(activity.rootView is FrameLayout)
        }
    }

    @Test
    fun rootHasAtLeastOneChild() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            assertTrue(activity.rootView!!.childCount >= 1)
        }
    }

    @Test
    fun lifecyclePauseSetsSchedulerPaused() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        // After launch (RESUMED), scheduler should not be paused
        assertFalse("Scheduler should not be paused after launch", SchedulerState.isPaused)
        // Move to STARTED triggers onPause
        scenario.moveToState(Lifecycle.State.STARTED)
        assertTrue("Scheduler should be paused after onPause", SchedulerState.isPaused)
    }

    @Test
    fun lifecycleResumeSetsSchedulerResumed() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        // Pause first
        scenario.moveToState(Lifecycle.State.STARTED)
        assertTrue("Scheduler should be paused", SchedulerState.isPaused)
        // Resume
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertFalse("Scheduler should be resumed after onResume", SchedulerState.isPaused)
    }

    @Test
    fun rotationRecreatesRootView() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.recreate()
        scenario.onActivity { activity ->
            assertNotNull(activity.rootView)
        }
    }
}
