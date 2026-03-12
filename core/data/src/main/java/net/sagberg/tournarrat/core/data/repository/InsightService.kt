package net.sagberg.tournarrat.core.data.repository

import java.util.UUID
import kotlinx.coroutines.flow.first
import net.sagberg.tournarrat.core.data.ai.AiClient
import net.sagberg.tournarrat.core.data.ai.DemoAiClient
import net.sagberg.tournarrat.core.data.ai.OpenAiClient
import net.sagberg.tournarrat.core.data.location.CurrentLocationProvider
import net.sagberg.tournarrat.core.data.places.PlaceContextProvider
import net.sagberg.tournarrat.core.data.preferences.InsightHistoryRepository
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepository
import net.sagberg.tournarrat.core.model.AiProvider
import net.sagberg.tournarrat.core.model.InsightRecord
import net.sagberg.tournarrat.core.model.InsightRequest

class InsightService(
    private val preferencesRepository: PreferencesRepository,
    private val historyRepository: InsightHistoryRepository,
    private val currentLocationProvider: CurrentLocationProvider,
    private val placeContextProvider: PlaceContextProvider,
    private val openAiClient: OpenAiClient,
    private val demoAiClient: DemoAiClient,
) {
    suspend fun generateInsightHere(): Result<InsightRecord> {
        val preferences = preferencesRepository.preferences.first()
        val location = currentLocationProvider.getCurrentLocation()
            .getOrElse { return Result.failure(it) }

        val placeContext = placeContextProvider.resolve(location)
        val client: AiClient = when (preferences.aiProvider) {
            AiProvider.OPEN_AI -> openAiClient
            AiProvider.DEMO -> demoAiClient
        }

        val primaryResult = client.generateInsight(
            InsightRequest(
                placeContext = placeContext,
                preferences = preferences,
            ),
        )
        val fallbackUsed = primaryResult.isFailure && preferences.aiProvider == AiProvider.OPEN_AI
        val draft = primaryResult.getOrElse {
            demoAiClient.generateInsight(
                InsightRequest(
                    placeContext = placeContext,
                    preferences = preferences,
                ),
            ).getOrThrow()
        }

        val record = InsightRecord(
            id = UUID.randomUUID().toString(),
            createdAtEpochMillis = System.currentTimeMillis(),
            placeContext = placeContext,
            title = draft.title,
            summary = draft.summary,
            whyItMatters = draft.whyItMatters,
            confidenceNote = draft.confidenceNote,
            followUps = draft.followUps,
            provider = if (fallbackUsed) AiProvider.DEMO else preferences.aiProvider,
            usedDemoFallback = fallbackUsed,
        )
        historyRepository.save(record)
        return Result.success(record)
    }
}
