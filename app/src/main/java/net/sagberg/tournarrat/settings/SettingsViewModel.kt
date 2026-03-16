package net.sagberg.tournarrat.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.sagberg.tournarrat.core.data.ai.OpenAiClient
import net.sagberg.tournarrat.core.data.narration.Narrator
import net.sagberg.tournarrat.core.data.narration.NarratorDiagnostics
import net.sagberg.tournarrat.core.data.preferences.ApiKeyStore
import net.sagberg.tournarrat.core.data.preferences.InsightHistoryRepository
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepository
import net.sagberg.tournarrat.core.model.AiProvider
import net.sagberg.tournarrat.core.model.AppPreferences
import net.sagberg.tournarrat.core.model.InsightFrequency
import net.sagberg.tournarrat.core.model.InsightTone
import net.sagberg.tournarrat.core.model.InterestTopic
import net.sagberg.tournarrat.core.model.OperatingMode

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val openAiKey: String = "",
    val googlePlacesKey: String = "",
    val narratorDiagnostics: NarratorDiagnostics? = null,
    val validationMessage: String? = null,
    val isValidating: Boolean = false,
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val historyRepository: InsightHistoryRepository,
    private val apiKeyStore: ApiKeyStore,
    private val openAiClient: OpenAiClient,
    private val narrator: Narrator,
) : ViewModel() {
    private val transientState = MutableStateFlow(
        SettingsUiState(
            openAiKey = apiKeyStore.getOpenAiApiKey().orEmpty(),
            googlePlacesKey = apiKeyStore.getGooglePlacesApiKey().orEmpty(),
        ),
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        transientState,
    ) { preferences, transient ->
        transient.copy(preferences = preferences)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = transientState.value,
    )

    init {
        refreshNarratorDiagnostics()
    }

    fun setMode(value: OperatingMode) = updatePrefs { copy(mode = value) }

    fun setFrequency(value: InsightFrequency) = updatePrefs { copy(frequency = value) }

    fun setTone(value: InsightTone) = updatePrefs { copy(tone = value) }

    fun setProvider(value: AiProvider) = updatePrefs { copy(aiProvider = value) }

    fun toggleInterest(topic: InterestTopic) = updatePrefs {
        val next = if (topic in interests) interests - topic else interests + topic
        copy(interests = if (next.isEmpty()) setOf(topic) else next)
    }

    fun setCustomPrompt(value: String) = updatePrefs { copy(customPrompt = value) }

    fun setOutputLanguage(value: String) = updatePrefs { copy(outputLanguage = value) }

    fun setTtsLocale(value: String?) {
        viewModelScope.launch {
            preferencesRepository.update { current -> current.copy(ttsLocaleTag = value) }
            refreshNarratorDiagnostics()
        }
    }

    fun setOpenAiKey(value: String) {
        transientState.update { current ->
            current.copy(openAiKey = value)
        }
    }

    fun setGooglePlacesKey(value: String) {
        transientState.update { current ->
            current.copy(googlePlacesKey = value)
        }
    }

    fun saveOpenAiKey() {
        apiKeyStore.setOpenAiApiKey(uiState.value.openAiKey)
        transientState.update { current ->
            current.copy(validationMessage = "OpenAI key saved locally.")
        }
    }

    fun clearOpenAiKey() {
        apiKeyStore.clearOpenAiApiKey()
        transientState.update { current ->
            current.copy(openAiKey = "", validationMessage = "OpenAI key cleared.")
        }
    }

    fun saveGooglePlacesKey() {
        apiKeyStore.setGooglePlacesApiKey(uiState.value.googlePlacesKey)
        transientState.update { current ->
            current.copy(validationMessage = "Google Places key saved locally.")
        }
    }

    fun clearGooglePlacesKey() {
        apiKeyStore.clearGooglePlacesApiKey()
        transientState.update { current ->
            current.copy(
                googlePlacesKey = "",
                validationMessage = "Google Places key cleared.",
            )
        }
    }

    fun validateOpenAiKey() {
        viewModelScope.launch {
            transientState.update { current ->
                current.copy(isValidating = true, validationMessage = null)
            }
            val result = openAiClient.validateCurrentKey()
            transientState.update { current ->
                current.copy(
                    isValidating = false,
                    validationMessage = result.fold(
                        onSuccess = { "OpenAI key validated successfully." },
                        onFailure = { it.message ?: "OpenAI key validation failed." },
                    ),
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clear()
            transientState.update { current ->
                current.copy(validationMessage = "Local history cleared.")
            }
        }
    }

    fun clearMessage() {
        transientState.update { current ->
            current.copy(validationMessage = null)
        }
    }

    fun refreshNarratorDiagnostics() {
        viewModelScope.launch {
            transientState.update { current ->
                current.copy(
                    narratorDiagnostics = narrator.diagnostics(),
                )
            }
        }
    }

    private fun updatePrefs(transform: AppPreferences.() -> AppPreferences) {
        viewModelScope.launch {
            preferencesRepository.update { current -> current.transform() }
        }
    }
}
