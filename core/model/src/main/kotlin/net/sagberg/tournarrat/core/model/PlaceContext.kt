package net.sagberg.tournarrat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaceContext(
    val latitude: Double,
    val longitude: Double,
    val areaName: String,
    val fullAddress: String? = null,
    val locality: String?,
    val countryName: String?,
    val hints: List<String> = emptyList(),
)
