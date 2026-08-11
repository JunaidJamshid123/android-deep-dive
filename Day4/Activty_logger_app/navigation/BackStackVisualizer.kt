package com.example.android_practice.Activty_logger_app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BackStackVisualizer {
    private val _stack = MutableStateFlow<List<String>>(emptyList())
    val stack: StateFlow<List<String>> = _stack

    fun push(activityName: String) {
        _stack.value = _stack.value + activityName
    }

    fun pop(activityName: String) {
        _stack.value = _stack.value.dropLastWhile { it == activityName }
    }
}