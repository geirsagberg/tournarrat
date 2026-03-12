package net.sagberg.tournarrat.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.sagberg.tournarrat.core.data.preferences.InsightHistoryRepository
import net.sagberg.tournarrat.core.model.InsightRecord

class HistoryViewModel(
    private val historyRepository: InsightHistoryRepository,
) : ViewModel() {
    val history: StateFlow<List<InsightRecord>> = historyRepository.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun delete(id: String) {
        viewModelScope.launch {
            historyRepository.delete(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clear()
        }
    }
}
