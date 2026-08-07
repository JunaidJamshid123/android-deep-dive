package com.example.android_practice.LiveScorePoller.data.datasource

import com.example.android_practice.LiveScorePoller.data.model.ScoreDto
import com.example.android_practice.LiveScorePoller.domain.model.SportCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

class FakeScoreDataSource {

	fun getScoresFlow(): Flow<List<ScoreDto>> = flow {
		val matches = mutableListOf(
			ScoreDto("football_1", SportCategory.FOOTBALL, "Man City", "Chelsea", 0, 0),
			ScoreDto("football_2", SportCategory.FOOTBALL, "Arsenal", "Liverpool", 0, 0),
			ScoreDto("tennis_1", SportCategory.TENNIS, "Djokovic", "Federer", 0, 0),
			ScoreDto("cricket_1", SportCategory.CRICKET, "India", "Australia", 0, 0),
			ScoreDto("basketball_1", SportCategory.BASKETBALL, "Lakers", "Bulls", 0, 0)
		)

		emit(matches.toList())

		while (true) {
			val index = Random.nextInt(matches.size)
			val current = matches[index]

			val homeIncrement = if (Random.nextFloat() < 0.10f) 1 else 0
			val awayIncrement = if (Random.nextFloat() < 0.08f) 1 else 0

			matches[index] = current.copy(
				homeGoals = current.homeGoals + homeIncrement,
				awayGoals = current.awayGoals + awayIncrement,
				lastUpdated = System.currentTimeMillis()
			)

			emit(matches.toList())
			delay(3_000)
		}
	}
}
