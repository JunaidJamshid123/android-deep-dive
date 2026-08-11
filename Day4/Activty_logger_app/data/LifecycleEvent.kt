package com.example.android_practice.Activty_logger_app.data

data class LifecycleEvent(
    val activityName: String,
    val event: String,
    val timestamp: Long = System.currentTimeMillis()
)