package com.example.android_practice.Activty_logger_app.lifecycle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.android_practice.Activty_logger_app.data.LifecycleTimelineStore
import com.example.android_practice.Activty_logger_app.navigation.BackStackVisualizer

abstract class BaseLoggingActivity : AppCompatActivity() {

    private val tag get() = this::class.simpleName ?: "Activity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LifecycleTimelineStore.log(tag, "onCreate")
        BackStackVisualizer.push(tag)
    }

    override fun onStart() {
        super.onStart()
        LifecycleTimelineStore.log(tag, "onStart")
    }

    override fun onResume() {
        super.onResume()
        LifecycleTimelineStore.log(tag, "onResume")
    }

    override fun onPause() {
        LifecycleTimelineStore.log(tag, "onPause")
        super.onPause()
    }

    override fun onStop() {
        LifecycleTimelineStore.log(tag, "onStop")
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        LifecycleTimelineStore.log(tag, "onRestart")
    }

    override fun onDestroy() {
        LifecycleTimelineStore.log(tag, "onDestroy")
        BackStackVisualizer.pop(tag)
        super.onDestroy()
    }
}