package com.example.android_practice.LiveScorePoller.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.android_practice.LiveScorePoller.data.datasource.FakeScoreDataSource
import com.example.android_practice.LiveScorePoller.data.repository.ScoreRepositoryImpl
import com.example.android_practice.LiveScorePoller.domain.usecase.ObserveScoresUseCase
import com.example.android_practice.LiveScorePoller.presentation.viewmodel.ScoreViewModel

// Manual DI graph — no Hilt; singletons via lazy delegation
object AppModule {

    private val dataSource by lazy { FakeScoreDataSource() }
    private val repository by lazy { ScoreRepositoryImpl(dataSource) }
    private val observeScoresUseCase by lazy { ObserveScoresUseCase(repository) }

    val scoreViewModelFactory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScoreViewModel(observeScoresUseCase) as T
        }
}
