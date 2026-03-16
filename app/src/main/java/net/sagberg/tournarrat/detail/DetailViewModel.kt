package net.sagberg.tournarrat.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.sagberg.tournarrat.core.data.narration.Narrator
import net.sagberg.tournarrat.core.data.preferences.InsightHistoryRepository
import net.sagberg.tournarrat.core.model.InsightRecord

data class DetailUiState(
    val isNarrating: Boolean = false,
)

class DetailViewModel(
    historyRepository: InsightHistoryRepository,
    private val narrator: Narrator,
) : ViewModel() {
    private val allHistory = historyRepository.history
    private val transientState = MutableStateFlow(DetailUiState())

    val uiState: StateFlow<DetailUiState> = transientState

    fun record(id: String): StateFlow<InsightRecord?> = allHistory
        .map { items -> items.firstOrNull { it.id == id } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun toggleNarration(record: InsightRecord?) {
        if (record == null) return
        if (transientState.value.isNarrating) {
            stopNarration()
        } else {
            speak(record)
        }
    }

    private fun speak(record: InsightRecord) {
        viewModelScope.launch {
            transientState.value = transientState.value.copy(isNarrating = true)
            narrator.speak("${record.title}. ${record.summary}")
        }
    }

    private fun stopNarration() {
        narrator.stop()
        transientState.value = transientState.value.copy(isNarrating = false)
    }
}
