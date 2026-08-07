package com.example.android_practice.LiveScorePoller.domain.repository

import com.example.android_practice.LiveScorePoller.domain.model.Score
import kotlinx.coroutines.flow.Flow

interface ScoreRepository {
	fun getScoresFlow(): Flow<List<Score>>
}
