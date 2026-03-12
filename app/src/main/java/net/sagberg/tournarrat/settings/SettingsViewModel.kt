package net.sagberg.tournarrat.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.sagberg.tournarrat.core.data.ai.OpenAiClient
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
    val validationMessage: String? = null,
    val isValidating: Boolean = false,
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val historyRepository: InsightHistoryRepository,
    private val apiKeyStore: ApiKeyStore,
    private val openAiClient: OpenAiClient,
) : ViewModel() {
    private val transientState = MutableStateFlow(SettingsUiState(openAiKey = apiKeyStore.getOpenAiApiKey().orEmpty()))

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        transientState,
    ) { preferences, transient ->
        transient.copy(preferences = preferences)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

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

    fun setOpenAiKey(value: String) {
        transientState.value = uiState.value.copy(openAiKey = value)
    }

    fun saveOpenAiKey() {
        apiKeyStore.setOpenAiApiKey(uiState.value.openAiKey)
        transientState.value = uiState.value.copy(validationMessage = "OpenAI key saved locally.")
    }

    fun clearOpenAiKey() {
        apiKeyStore.clearOpenAiApiKey()
        transientState.value = uiState.value.copy(openAiKey = "", validationMessage = "OpenAI key cleared.")
    }

    fun validateOpenAiKey() {
        viewModelScope.launch {
            transientState.value = uiState.value.copy(isValidating = true, validationMessage = null)
            val result = openAiClient.validateCurrentKey()
            transientState.value = uiState.value.copy(
                isValidating = false,
                validationMessage = result.fold(
                    onSuccess = { "OpenAI key validated successfully." },
                    onFailure = { it.message ?: "OpenAI key validation failed." },
                ),
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clear()
            transientState.value = uiState.value.copy(validationMessage = "Local history cleared.")
        }
    }

    fun clearMessage() {
        transientState.value = uiState.value.copy(validationMessage = null)
    }

    private fun updatePrefs(transform: AppPreferences.() -> AppPreferences) {
        viewModelScope.launch {
            preferencesRepository.update { current -> current.transform() }
        }
    }
}
