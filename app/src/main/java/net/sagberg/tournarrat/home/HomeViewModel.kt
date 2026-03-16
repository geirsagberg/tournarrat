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
import net.sagberg.tournarrat.core.model.OperatingMode

data class HomeUiState(
    val preferences: AppPreferences = AppPreferences(),
    val isGenerating: Boolean = false,
    val isNarrating: Boolean = false,
    val isDiagnosticsVisible: Boolean = false,
    val isInsightSheetVisible: Boolean = false,
    val mapState: HomeMapState = HomeMapState(),
    val latestInsight: InsightRecord? = null,
    val latestResolvedPlace: ResolvedPlaceDebug? = null,
    val errorMessage: String? = null,
)

data class HomeMapState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val label: String? = null,
    val fullAddress: String? = null,
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

    init {
        refreshCurrentPlace()
    }

    fun refreshCurrentPlace() {
        viewModelScope.launch {
            insightService.resolveCurrentPlace()
                .onSuccess { placeContext ->
                    transientState.value = uiState.value.copy(
                        mapState = HomeMapState(
                            latitude = placeContext.latitude,
                            longitude = placeContext.longitude,
                            label = placeContext.areaName,
                            fullAddress = placeContext.fullAddress,
                        ),
                        latestResolvedPlace = ResolvedPlaceDebug(
                            placeContext = placeContext,
                            updatedAt = Instant.now(),
                        ),
                    )
                }
        }
    }

    fun generateInsight() {
        viewModelScope.launch {
            transientState.value = uiState.value.copy(
                isGenerating = true,
                isInsightSheetVisible = false,
                errorMessage = null,
            )
            val result = insightService.generateInsightHere()
            transientState.value = if (result.isSuccess) {
                val record = result.getOrNull()
                uiState.value.copy(
                    isGenerating = false,
                    isNarrating = false,
                    isInsightSheetVisible = record != null,
                    mapState = record?.placeContext?.let { place ->
                        HomeMapState(
                            latitude = place.latitude,
                            longitude = place.longitude,
                            label = place.areaName,
                            fullAddress = place.fullAddress,
                        )
                    } ?: uiState.value.mapState,
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

    fun setMode(mode: OperatingMode) {
        viewModelScope.launch {
            preferencesRepository.update { current -> current.copy(mode = mode) }
        }
    }

    fun showDiagnostics() {
        transientState.value = uiState.value.copy(isDiagnosticsVisible = true)
    }

    fun hideDiagnostics() {
        transientState.value = uiState.value.copy(isDiagnosticsVisible = false)
    }

    fun showLatestInsight() {
        if (uiState.value.latestInsight != null) {
            transientState.value = uiState.value.copy(isInsightSheetVisible = true)
        }
    }

    fun hideLatestInsight() {
        transientState.value = uiState.value.copy(isInsightSheetVisible = false)
    }
}
