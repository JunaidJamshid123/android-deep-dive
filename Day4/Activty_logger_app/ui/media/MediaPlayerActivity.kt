package com.example.android_practice.Activty_logger_app.ui.media

import android.os.Build
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.android_practice.Activty_logger_app.lifecycle.BaseLoggingActivity
import com.example.android_practice.R

class MediaPlayerActivity : BaseLoggingActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
        playerView = findViewById(R.id.playerView)
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.setMediaItem(MediaItem.fromUri(SAMPLE_AUDIO_URI))
            it.prepare()
        }
    }

    override fun onStart() {
        super.onStart()
        // Android 7+ supports multi-window / picture-in-picture where the app
        // can be visible but not resumed — start playback here, not in onResume,
        // so PiP playback isn't interrupted unnecessarily.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) player.play()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) player.play()
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) player.pause()
        super.onPause()
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val SAMPLE_AUDIO_URI = "https://example.com/sample.mp3"
    }
}