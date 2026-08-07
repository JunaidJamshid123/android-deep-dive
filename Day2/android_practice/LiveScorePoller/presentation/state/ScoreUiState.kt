package com.example.android_practice.LiveScorePoller.presentation.state

import com.example.android_practice.LiveScorePoller.domain.model.Score
import com.example.android_practice.LiveScorePoller.domain.model.SportCategory

data class ScoreUiState(
	val scores: List<Score> = emptyList(),
	val selectedSport: SportCategory = SportCategory.ALL,
	val isLoading: Boolean = true,
	val errorMessage: String? = null
)
