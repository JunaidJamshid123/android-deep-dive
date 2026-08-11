package com.example.android_practice.Activty_logger_app.ui.deeplink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import com.example.android_practice.Activty_logger_app.data.LifecycleTimelineStore
import com.example.android_practice.Activty_logger_app.lifecycle.BaseLoggingActivity
import com.example.android_practice.R


class DeepLinkCounterActivity : BaseLoggingActivity() {

    private lateinit var counterTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter)
        counterTextView = findViewById(R.id.counterTextView)
        handleDeepLink(intent)
    }

    // If this Activity is singleTop/singleTask and already open,
    // a new deep link arrives here instead of a new onCreate().
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val uri: Uri? = intent.data
        if (uri != null) {
            val startAt = uri.getQueryParameter("startAt")?.toIntOrNull() ?: 0
            LifecycleTimelineStore.log("DeepLinkCounter", "opened via deep link, startAt=$startAt")
            counterTextView.text = "Started via deep link at $startAt"
        }
    }
}