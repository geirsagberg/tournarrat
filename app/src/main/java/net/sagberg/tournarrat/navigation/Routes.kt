package net.sagberg.tournarrat.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

@Serializable
data class DetailRoute(
    val insightId: String,
)
