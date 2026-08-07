package com.example.android_practice.LiveScorePoller.data.repository

import com.example.android_practice.LiveScorePoller.data.datasource.FakeScoreDataSource
import com.example.android_practice.LiveScorePoller.data.model.ScoreDto
import com.example.android_practice.LiveScorePoller.domain.model.Score
import com.example.android_practice.LiveScorePoller.domain.repository.ScoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ScoreRepositoryImpl(
	private val dataSource: FakeScoreDataSource
) : ScoreRepository {

	override fun getScoresFlow(): Flow<List<Score>> =
		dataSource.getScoresFlow()
			.map { scoreDtos -> scoreDtos.map { it.toDomain() } }
			.flowOn(Dispatchers.IO)

	private fun ScoreDto.toDomain(): Score {
		return Score(
			id = id,
			sport = sport,
			homeTeam = homeTeam,
			awayTeam = awayTeam,
			homeGoals = homeGoals,
			awayGoals = awayGoals,
			lastUpdated = lastUpdated
		)
	}
}
