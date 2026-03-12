package net.sagberg.tournarrat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val onboardingCompleted: Boolean = false,
    val mode: OperatingMode = OperatingMode.POPUPS,
    val frequency: InsightFrequency = InsightFrequency.LOW,
    val tone: InsightTone = InsightTone.FACTUAL,
    val interests: Set<InterestTopic> = InterestTopic.defaultSelection,
    val customPrompt: String = "",
    val outputLanguage: String = "English",
    val aiProvider: AiProvider = AiProvider.OPEN_AI,
)

@Serializable
enum class OperatingMode {
    LIVE,
    POPUPS,
}

@Serializable
enum class InsightFrequency {
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
enum class InsightTone {
    FACTUAL,
    GUIDE_LIKE,
    PLAYFUL,
    CONCISE,
}

@Serializable
enum class AiProvider {
    OPEN_AI,
    DEMO,
}

@Serializable
enum class InterestTopic {
    HISTORY,
    ARCHITECTURE,
    CULTURE,
    FOOD,
    NATURE,
    HIDDEN_GEMS,
    TRIVIA;

    val label: String
        get() = when (this) {
            HISTORY -> "History"
            ARCHITECTURE -> "Architecture"
            CULTURE -> "Culture"
            FOOD -> "Food"
            NATURE -> "Nature"
            HIDDEN_GEMS -> "Hidden gems"
            TRIVIA -> "Trivia"
        }

    companion object {
        val defaultSelection: Set<InterestTopic> = setOf(
            HISTORY,
            ARCHITECTURE,
            CULTURE,
            HIDDEN_GEMS,
        )
    }
}
