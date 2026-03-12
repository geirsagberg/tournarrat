package net.sagberg.tournarrat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepository

class RootViewModel(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val onboardingCompleted: StateFlow<Boolean> = preferencesRepository.preferences
        .map { it.onboardingCompleted }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )
}
