package com.example.android_practice.Activty_logger_app.ui.launchmode

import android.os.Bundle
import android.widget.TextView
import com.example.android_practice.Activty_logger_app.lifecycle.BaseLoggingActivity
import com.example.android_practice.R

// android:launchMode="standard" in the manifest — every startActivity() call
// creates a brand-new instance, even if one already exists on the stack.
class StandardCounterActivity : BaseLoggingActivity() {
    private var launchCount = 0
    private lateinit var counterTextView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter)
        counterTextView = findViewById(R.id.counterTextView)

        launchCount++
        updateUi()
    }

    private fun updateUi() {
        counterTextView.text = "New instance #$launchCount"
    }
}