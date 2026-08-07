package com.example.android_practice.LiveScorePoller.domain.usecase

import com.example.android_practice.LiveScorePoller.domain.model.Score
import com.example.android_practice.LiveScorePoller.domain.repository.ScoreRepository
import kotlinx.coroutines.flow.Flow

class ObserveScoresUseCase(
	private val repository: ScoreRepository
) {
	operator fun invoke(): Flow<List<Score>> = repository.getScoresFlow()
}
