package net.sagberg.tournarrat.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import net.sagberg.tournarrat.core.model.PlaceContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.sagberg.tournarrat.core.data.narration.Narrator
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepository
import net.sagberg.tournarrat.core.data.repository.InsightService
import net.sagberg.tournarrat.core.model.AppPreferences
import net.sagberg.tournarrat.core.model.InsightRecord

data class HomeUiState(
    val preferences: AppPreferences = AppPreferences(),
    val isGenerating: Boolean = false,
    val isNarrating: Boolean = false,
    val latestInsight: InsightRecord? = null,
    val latestResolvedPlace: ResolvedPlaceDebug? = null,
    val errorMessage: String? = null,
)

data class ResolvedPlaceDebug(
    val placeContext: PlaceContext,
    val updatedAt: Instant,
)

class HomeViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val insightService: InsightService,
    private val narrator: Narrator,
) : ViewModel() {
    private val transientState = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(
        preferencesRepository.preferences,
        transientState,
    ) { preferences, transient ->
        transient.copy(preferences = preferences)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun generateInsight() {
        viewModelScope.launch {
            transientState.value = uiState.value.copy(isGenerating = true, errorMessage = null)
            val result = insightService.generateInsightHere()
            transientState.value = if (result.isSuccess) {
                val record = result.getOrNull()
                uiState.value.copy(
                    isGenerating = false,
                    isNarrating = false,
                    latestInsight = record,
                    latestResolvedPlace = record?.let {
                        ResolvedPlaceDebug(
                            placeContext = it.placeContext,
                            updatedAt = Instant.ofEpochMilli(it.createdAtEpochMillis),
                        )
                    },
                    errorMessage = null,
                )
            } else {
                uiState.value.copy(
                    isGenerating = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Unable to generate insight.",
                )
            }
        }
    }

    fun speakLatestInsight() {
        val insight = uiState.value.latestInsight ?: return
        viewModelScope.launch {
            transientState.value = uiState.value.copy(isNarrating = true)
            narrator.speak("${insight.title}. ${insight.summary}")
        }
    }

    fun stopNarration() {
        narrator.stop()
        transientState.value = uiState.value.copy(isNarrating = false)
    }

    fun toggleNarration() {
        if (uiState.value.isNarrating) {
            stopNarration()
        } else {
            speakLatestInsight()
        }
    }

    fun clearMessage() {
        transientState.value = uiState.value.copy(errorMessage = null)
    }
}
