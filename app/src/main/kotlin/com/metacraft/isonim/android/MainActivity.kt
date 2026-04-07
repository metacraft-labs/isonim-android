package com.metacraft.isonim.android

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        val label = TextView(this).apply {
            text = "IsoNim Android"
            textSize = 24f
        }
        root.addView(label)
        setContentView(root)
    }
}
