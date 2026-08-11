package com.example.android_practice.Activty_logger_app.ui.launchmode

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.example.android_practice.Activty_logger_app.data.LifecycleTimelineStore
import com.example.android_practice.Activty_logger_app.lifecycle.BaseLoggingActivity
import com.example.android_practice.R


// android:launchMode="singleTask" in the manifest — only one instance can
// exist in the task; reusing it clears everything above and calls onNewIntent().
class SingleTaskCounterActivity : BaseLoggingActivity() {
    private var launchCount = 0
    private lateinit var counterTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter)
        counterTextView = findViewById(R.id.counterTextView)
        launchCount++
        updateUi()
    }

    // Called instead of onCreate() when the existing singleTask instance
    // is reused rather than a new one created.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LifecycleTimelineStore.log("SingleTaskCounter", "onNewIntent (reused instance)")
        launchCount++
        updateUi()
    }

    private fun updateUi() {
        counterTextView.text = "Instance reused $launchCount time(s)"
    }
}