package com.example.android_practice.LiveScorePoller.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android_practice.LiveScorePoller.domain.model.Score
import com.example.android_practice.LiveScorePoller.domain.model.SportCategory
import com.example.android_practice.LiveScorePoller.domain.usecase.ObserveScoresUseCase
import com.example.android_practice.LiveScorePoller.presentation.event.ScoreEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScoreViewModel(
	private val observeScoresUseCase: ObserveScoresUseCase
) : ViewModel() {

	private val _allScores = MutableStateFlow<List<Score>>(emptyList())
	private val _selectedSport = MutableStateFlow(SportCategory.ALL)
	private val _events = MutableSharedFlow<ScoreEvent>(
		replay = 0,
		extraBufferCapacity = 10
	)

	val events: SharedFlow<ScoreEvent> = _events.asSharedFlow()

	val filteredScores: StateFlow<List<Score>> = combine(
		_selectedSport,
		_allScores
	) { sport, scores ->
		if (sport == SportCategory.ALL) {
			scores
		} else {
			scores.filter { it.sport == sport }
		}
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = emptyList()
	)

	init {
		startCollecting()
	}

	private fun startCollecting() {
		viewModelScope.launch {
			observeScoresUseCase()
				.catch {
					_events.emit(ScoreEvent.NetworkError)
				}
				.collect { newScores ->
					detectGoals(newScores)
					_allScores.value = newScores
				}
		}
	}

	private fun detectGoals(newScores: List<Score>) {
		val previousScores = _allScores.value.associateBy { it.id }

		newScores.forEach { newScore ->
			val oldScore = previousScores[newScore.id] ?: return@forEach

			if (newScore.homeGoals > oldScore.homeGoals) {
				viewModelScope.launch {
					_events.emit(
						ScoreEvent.GoalScored(
							sport = newScore.sport,
							scoringTeam = newScore.homeTeam,
							matchTitle = newScore.displayTitle,
							newScore = newScore.displayScore
						)
					)
				}
			}

			if (newScore.awayGoals > oldScore.awayGoals) {
				viewModelScope.launch {
					_events.emit(
						ScoreEvent.GoalScored(
							sport = newScore.sport,
							scoringTeam = newScore.awayTeam,
							matchTitle = newScore.displayTitle,
							newScore = newScore.displayScore
						)
					)
				}
			}
		}
	}

	fun selectSport(sport: SportCategory) {
		_selectedSport.update { sport }
	}
}
