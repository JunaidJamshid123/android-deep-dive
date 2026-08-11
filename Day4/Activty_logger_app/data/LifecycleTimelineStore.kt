package com.example.android_practice.Activty_logger_app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LifecycleTimelineStore {
    private val _events = MutableStateFlow<List<LifecycleEvent>>(emptyList())
    val events: StateFlow<List<LifecycleEvent>> = _events

    fun log(activityName: String, event: String) {
        _events.value = _events.value + LifecycleEvent(activityName, event)
    }
}