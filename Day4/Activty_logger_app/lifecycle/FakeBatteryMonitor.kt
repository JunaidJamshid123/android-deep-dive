package com.example.android_practice.Activty_logger_app.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.android_practice.Activty_logger_app.data.LifecycleTimelineStore

class FakeBatteryMonitor(
    private val onLevelChanged: (Int) -> Unit
) : DefaultLifecycleObserver {

    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var level = 100

    override fun onStart(owner: LifecycleOwner) {
        LifecycleTimelineStore.log("BatteryMonitor", "started polling")
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                level = (level - 1).coerceAtLeast(0)
                onLevelChanged(level)
                handler?.postDelayed(this, 2000)
            }
        }
        handler?.post(runnable!!)
    }

    override fun onStop(owner: LifecycleOwner) {
        LifecycleTimelineStore.log("BatteryMonitor", "stopped polling")
        runnable?.let { handler?.removeCallbacks(it) }
    }
}