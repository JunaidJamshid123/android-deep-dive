package com.example.android_practice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.android_practice.LiveScorePoller.presentation.screen.ScoreListScreen
import com.example.android_practice.ui.theme.Android_practiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Android_practiceTheme {
                ScoreListScreen()
            }
        }
    }
}