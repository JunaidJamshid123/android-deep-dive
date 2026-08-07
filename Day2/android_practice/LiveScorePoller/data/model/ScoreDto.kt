package com.example.android_practice.LiveScorePoller.data.model

import com.example.android_practice.LiveScorePoller.domain.model.SportCategory

data class ScoreDto(
	val id: String,
	val sport: SportCategory,
	val homeTeam: String,
	val awayTeam: String,
	val homeGoals: Int,
	val awayGoals: Int,
	val lastUpdated: Long = System.currentTimeMillis()
)
