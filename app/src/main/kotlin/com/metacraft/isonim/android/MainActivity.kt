package com.metacraft.isonim.android

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    var rootView: FrameLayout? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootView = FrameLayout(this)
        val label = TextView(this).apply {
            text = "IsoNim Android"
            textSize = 24f
        }
        rootView!!.addView(label)
        setContentView(rootView)
        SchedulerState.resumed()
    }

    override fun onPause() {
        super.onPause()
        SchedulerState.paused()
    }

    override fun onResume() {
        super.onResume()
        SchedulerState.resumed()
    }
}

/** Kotlin-side scheduler state tracker.
 *  In production, this would call into Nim via JNI to pause/resume
 *  the native scheduler. For now, it tracks state for testing. */
object SchedulerState {
    var isPaused: Boolean = false
        private set

    fun paused() { isPaused = true }
    fun resumed() { isPaused = false }
    fun reset() { isPaused = false }
}
