package net.sagberg.tournarrat.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepository

class OnboardingViewModel(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.update { it.copy(onboardingCompleted = true) }
        }
    }
}
