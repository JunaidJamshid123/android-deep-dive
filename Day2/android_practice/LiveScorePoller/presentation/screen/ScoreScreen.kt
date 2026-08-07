package com.example.android_practice.LiveScorePoller.presentation.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.android_practice.LiveScorePoller.di.AppModule
import com.example.android_practice.LiveScorePoller.domain.model.Score
import com.example.android_practice.LiveScorePoller.domain.model.SportCategory
import com.example.android_practice.LiveScorePoller.presentation.event.ScoreEvent
import com.example.android_practice.LiveScorePoller.presentation.viewmodel.ScoreViewModel

@Composable
fun ScoreListScreen(
    viewModel: ScoreViewModel = viewModel(factory = AppModule.scoreViewModelFactory)
) {
    // collectAsState() converts StateFlow → Compose State so the UI recomposes on each emission
    val scores by viewModel.filteredScores.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // LaunchedEffect(Unit) launches a coroutine once (when Unit key never changes)
    LaunchedEffect(Unit) {
        // collect{} on SharedFlow — suspends and handles each one-time event
        viewModel.events.collect { event ->
            when (event) {
                is ScoreEvent.GoalScored ->
                    snackbarHostState.showSnackbar("⚽ GOAL! ${event.scoringTeam}: ${event.newScore}")
                ScoreEvent.NetworkError ->
                    snackbarHostState.showSnackbar("Connection lost...")
                else -> {}
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SportFilterRow(onSportSelected = { viewModel.selectSport(it) })
            ScoreList(scores = scores)
        }
    }
}

@Composable
fun SportFilterRow(onSportSelected: (SportCategory) -> Unit) {
    var selected by remember { mutableStateOf(SportCategory.ALL) }
    val categories = SportCategory.entries

    ScrollableTabRow(selectedTabIndex = categories.indexOf(selected)) {
        categories.forEach { sport ->
            Tab(
                selected = sport == selected,
                onClick = {
                    selected = sport
                    onSportSelected(sport)
                },
                text = { Text(sport.name) }
            )
        }
    }
}

@Composable
fun ScoreList(scores: List<Score>) {
    // Plain MutableMap (not Compose state) — reads don't register as snapshot reads,
    // so updating it in SideEffect won't trigger an extra recomposition.
    val previousById = remember { mutableMapOf<String, Score>() }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(scores, key = { it.id }) { score ->
            ScoreCard(
                score = score,
                previousScore = previousById[score.id]   // old snapshot read during layout
            )
        }
    }

    // SideEffect runs after every successful recomposition — update previous for next diff
    SideEffect {
        previousById.clear()
        previousById.putAll(scores.associateBy { it.id })
    }
}

@Composable
fun ScoreCard(score: Score, previousScore: Score?) {
    val hasChanged = previousScore != null &&
        (score.homeGoals != previousScore.homeGoals ||
         score.awayGoals != previousScore.awayGoals)

    // animateColorAsState() — smoothly transitions background when a goal is detected
    val backgroundColor by animateColorAsState(
        targetValue = if (hasChanged) Color(0xFFFFF176) else Color.White,
        animationSpec = tween(durationMillis = 600),
        label = "score_background"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = score.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = score.displayScore,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = score.sport.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
