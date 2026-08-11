package com.example.android_practice.Activty_logger_app.ui.home

import android.os.Bundle
import android.widget.TextView
import com.example.android_practice.Activty_logger_app.lifecycle.BaseLoggingActivity
import com.example.android_practice.Activty_logger_app.lifecycle.FakeBatteryMonitor
import com.example.android_practice.R


class HomeActivity : BaseLoggingActivity() {
    private lateinit var batteryTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // logs "onCreate" automatically
        setContentView(R.layout.activity_home)

        batteryTextView = findViewById(R.id.batteryTextView)
        val monitor = FakeBatteryMonitor { level ->
            batteryTextView.text = "Battery: $level%"
        }
        lifecycle.addObserver(monitor)
    }
}