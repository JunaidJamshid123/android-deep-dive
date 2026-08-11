package com.example.android_practice.Activty_logger_app.ui.counter

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.android_practice.R
import com.example.android_practice.Activty_logger_app.lifecycle.BaseLoggingActivity
import kotlinx.coroutines.launch

class CounterActivity : BaseLoggingActivity() {
    private val viewModel: CounterViewModel by viewModels()
    private lateinit var counterTextView: TextView
    private lateinit var incrementButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter)

        counterTextView = findViewById(R.id.counterTextView)
        incrementButton = findViewById(R.id.incrementButton)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.count.collect { counterTextView.text = "Count: $it" }
            }
        }
        incrementButton.setOnClickListener { viewModel.increment() }
    }
}