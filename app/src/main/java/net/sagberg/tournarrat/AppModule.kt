package net.sagberg.tournarrat

import net.sagberg.tournarrat.detail.DetailViewModel
import net.sagberg.tournarrat.history.HistoryViewModel
import net.sagberg.tournarrat.home.HomeViewModel
import net.sagberg.tournarrat.onboarding.OnboardingViewModel
import net.sagberg.tournarrat.settings.SettingsViewModel
import net.sagberg.tournarrat.ui.RootViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::RootViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::DetailViewModel)
    viewModelOf(::SettingsViewModel)
}
