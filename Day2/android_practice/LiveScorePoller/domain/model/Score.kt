package com.example.android_practice.LiveScorePoller.domain.model

data class Score(
	val id: String,
	val sport: SportCategory,
	val homeTeam: String,
	val awayTeam: String,
	val homeGoals: Int,
	val awayGoals: Int,
	val lastUpdated: Long
) {
	val displayTitle: String get() = "$homeTeam vs $awayTeam"
	val displayScore: String get() = "$homeGoals - $awayGoals"
}
