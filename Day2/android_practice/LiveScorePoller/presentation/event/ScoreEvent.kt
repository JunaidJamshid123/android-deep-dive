package com.example.android_practice.LiveScorePoller.presentation.event

import com.example.android_practice.LiveScorePoller.domain.model.SportCategory

sealed class ScoreEvent {
	data class GoalScored(
		val sport: SportCategory,
		val scoringTeam: String,
		val matchTitle: String,
		val newScore: String
	) : ScoreEvent()

	object NetworkError : ScoreEvent()
}
