package net.sagberg.tournarrat.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import net.sagberg.tournarrat.core.data.narration.NarratorDiagnostics
import net.sagberg.tournarrat.core.model.PlaceContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.sagberg.tournarrat.core.data.narration.Narrator
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepository
import net.sagberg.tournarrat.core.data.repository.InsightService
import net.sagberg.tournarrat.core.model.AppPreferences
import net.sagberg.tournarrat.core.model.InsightRecord
import net.sagberg.tournarrat.core.model.OperatingMode

data class HomeUiState(
    val preferences: AppPreferences = AppPreferences(),
    val isResolvingPlace: Boolean = true,
    val isGenerating: Boolean = false,
    val isNarrating: Boolean = false,
    val isDiagnosticsVisible: Boolean = false,
    val isInsightSheetVisible: Boolean = false,
    val mapState: HomeMapState = HomeMapState(),
    val latestInsight: InsightRecord? = null,
    val latestResolvedPlace: ResolvedPlaceDebug? = null,
    val narratorDiagnostics: NarratorDiagnostics? = null,
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
        val cachedPlace = preferences.cachedPlaceContext
        val cachedUpdatedAt = preferences.cachedPlaceUpdatedAtEpochMillis
        val effectiveMapState = if (transient.mapState.latitude != null && transient.mapState.longitude != null) {
            transient.mapState
        } else {
            cachedPlace?.toMapState() ?: transient.mapState
        }
        val effectiveResolvedPlace = transient.latestResolvedPlace
            ?: if (cachedPlace != null && cachedUpdatedAt != null) {
                ResolvedPlaceDebug(
                    placeContext = cachedPlace,
                    updatedAt = Instant.ofEpochMilli(cachedUpdatedAt),
                )
            } else {
                null
            }
        transient.copy(
            preferences = preferences,
            mapState = effectiveMapState,
            latestResolvedPlace = effectiveResolvedPlace,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refreshCurrentPlace()
        refreshNarratorDiagnostics()
    }

    fun refreshCurrentPlace() {
        viewModelScope.launch {
            transientState.update { current ->
                current.copy(isResolvingPlace = true)
            }
            insightService.resolveCurrentPlace()
                .onSuccess { placeContext ->
                    persistResolvedPlace(placeContext, Instant.now())
                    transientState.update { current ->
                        current.copy(
                            isResolvingPlace = false,
                            mapState = placeContext.toMapState(),
                            latestResolvedPlace = ResolvedPlaceDebug(
                                placeContext = placeContext,
                                updatedAt = Instant.now(),
                            ),
                        )
                    }
                }
                .onFailure {
                    transientState.update { current ->
                        current.copy(isResolvingPlace = false)
                    }
                }
        }
    }

    fun refreshNarratorDiagnostics() {
        viewModelScope.launch {
            val diagnostics = narrator.diagnostics()
            transientState.update { current ->
                current.copy(narratorDiagnostics = diagnostics)
            }
        }
    }

    fun generateInsight() {
        viewModelScope.launch {
            transientState.update { current ->
                current.copy(
                    isGenerating = true,
                    isInsightSheetVisible = false,
                    errorMessage = null,
                )
            }
            val result = insightService.generateInsightHere()
            if (result.isSuccess) {
                val record = result.getOrNull()
                record?.let {
                    persistResolvedPlace(
                        placeContext = it.placeContext,
                        updatedAt = Instant.ofEpochMilli(it.createdAtEpochMillis),
                    )
                }
                transientState.update { current ->
                    current.copy(
                        isGenerating = false,
                        isNarrating = false,
                        isInsightSheetVisible = record != null,
                        mapState = record?.placeContext?.toMapState() ?: current.mapState,
                        latestInsight = record,
                        latestResolvedPlace = record?.let {
                            ResolvedPlaceDebug(
                                placeContext = it.placeContext,
                                updatedAt = Instant.ofEpochMilli(it.createdAtEpochMillis),
                            )
                        } ?: current.latestResolvedPlace,
                        errorMessage = null,
                    )
                }
            } else {
                transientState.update { current ->
                    current.copy(
                        isGenerating = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to generate insight.",
                    )
                }
            }
        }
    }

    fun speakLatestInsight() {
        val insight = uiState.value.latestInsight ?: return
        viewModelScope.launch {
            transientState.update { current ->
                current.copy(isNarrating = true)
            }
            narrator.speak("${insight.title}. ${insight.summary}")
        }
    }

    fun stopNarration() {
        narrator.stop()
        transientState.update { current ->
            current.copy(isNarrating = false)
        }
    }

    fun toggleNarration() {
        if (uiState.value.isNarrating) {
            stopNarration()
        } else {
            speakLatestInsight()
        }
    }

    fun clearMessage() {
        transientState.update { current ->
            current.copy(errorMessage = null)
        }
    }

    fun setMode(mode: OperatingMode) {
        viewModelScope.launch {
            preferencesRepository.update { current -> current.copy(mode = mode) }
        }
    }

    fun showDiagnostics() {
        refreshNarratorDiagnostics()
        transientState.update { current ->
            current.copy(isDiagnosticsVisible = true)
        }
    }

    fun hideDiagnostics() {
        transientState.update { current ->
            current.copy(isDiagnosticsVisible = false)
        }
    }

    fun showLatestInsight() {
        if (uiState.value.latestInsight != null) {
            transientState.update { current ->
                current.copy(isInsightSheetVisible = true)
            }
        }
    }

    fun hideLatestInsight() {
        transientState.update { current ->
            current.copy(isInsightSheetVisible = false)
        }
    }

    private fun persistResolvedPlace(
        placeContext: PlaceContext,
        updatedAt: Instant,
    ) {
        viewModelScope.launch {
            preferencesRepository.update { current ->
                current.copy(
                    cachedPlaceContext = placeContext,
                    cachedPlaceUpdatedAtEpochMillis = updatedAt.toEpochMilli(),
                )
            }
        }
    }
}

private fun PlaceContext.toMapState(): HomeMapState = HomeMapState(
    latitude = latitude,
    longitude = longitude,
    label = areaName,
    fullAddress = fullAddress,
)
