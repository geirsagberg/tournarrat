package net.sagberg.tournarrat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class InsightDraft(
    val title: String,
    val summary: String,
    val whyItMatters: String,
    val confidenceNote: String,
    val followUps: List<String> = emptyList(),
)

@Serializable
data class InsightGenerationMetadata(
    val tone: InsightTone,
    val interests: Set<InterestTopic>,
    val customPrompt: String = "",
)

@Serializable
data class InsightRecord(
    val id: String,
    val createdAtEpochMillis: Long,
    val placeContext: PlaceContext,
    val title: String,
    val summary: String,
    val whyItMatters: String,
    val confidenceNote: String,
    val followUps: List<String>,
    val provider: AiProvider,
    val usedDemoFallback: Boolean,
    val generationMetadata: InsightGenerationMetadata? = null,
)

@Serializable
data class InsightHistory(
    val items: List<InsightRecord> = emptyList(),
)
