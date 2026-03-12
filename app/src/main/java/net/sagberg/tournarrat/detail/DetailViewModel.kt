package net.sagberg.tournarrat.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.sagberg.tournarrat.core.data.preferences.InsightHistoryRepository
import net.sagberg.tournarrat.core.model.InsightRecord

class DetailViewModel(
    historyRepository: InsightHistoryRepository,
) : ViewModel() {
    private val allHistory = historyRepository.history

    fun record(id: String): StateFlow<InsightRecord?> = allHistory
        .map { items -> items.firstOrNull { it.id == id } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )
}
