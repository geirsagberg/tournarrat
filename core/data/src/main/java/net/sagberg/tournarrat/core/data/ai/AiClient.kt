package net.sagberg.tournarrat.core.data.ai

import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.sagberg.tournarrat.core.data.preferences.ApiKeyStore
import net.sagberg.tournarrat.core.model.AppPreferences
import net.sagberg.tournarrat.core.model.InsightDraft
import net.sagberg.tournarrat.core.model.InsightRequest
import net.sagberg.tournarrat.core.model.InterestTopic

interface AiClient {
    suspend fun generateInsight(request: InsightRequest): Result<InsightDraft>
}

interface OpenAiKeyValidator {
    suspend fun validateCurrentKey(): Result<Unit>
}

class DemoAiClient : AiClient {
    override suspend fun generateInsight(request: InsightRequest): Result<InsightDraft> {
        val area = request.placeContext.areaName
        val locality = request.placeContext.locality ?: "this area"
        val firstInterest = request.preferences.interests.firstOrNull()?.label ?: "history"
        return Result.success(
            InsightDraft(
                title = "A quick read on $area",
                summary = "$area looks like a strong place for $firstInterest. Use it as a waypoint and notice how it relates to $locality around you.",
                whyItMatters = "This is a local demo insight so the app stays testable before you add a live AI key.",
                confidenceNote = "Demo mode: grounded only in your current area label and nearby address hints.",
                followUps = listOf(
                    "What should I look for nearby?",
                    "Give me a shorter live-mode version.",
                ),
            ),
        )
    }
}

class OpenAiClient(
    private val apiKeyStore: ApiKeyStore,
    private val json: Json,
) : AiClient, OpenAiKeyValidator {
    override suspend fun generateInsight(request: InsightRequest): Result<InsightDraft> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getOpenAiApiKey()
            ?: return@withContext Result.failure(IllegalStateException("Add an OpenAI API key in Settings first."))

        runCatching {
            val connection = createConnection(apiKey)
            connection.outputStream.use { output ->
                output.writer().use { writer ->
                    writer.write(json.encodeToString(buildRequest(request.preferences, request)))
                }
            }

            val payload = connection.readResponseText()
            if (connection.responseCode !in 200..299) {
                error(payload.ifBlank { "OpenAI request failed with ${connection.responseCode}." })
            }

            val response = json.decodeFromString<OpenAiChatCompletionResponse>(payload)
            val content = response.choices.firstOrNull()?.message?.content
                ?: error("OpenAI response did not contain assistant content.")
            json.decodeFromString<InsightDraft>(content)
        }
    }

    override suspend fun validateCurrentKey(): Result<Unit> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getOpenAiApiKey()
            ?: return@withContext Result.failure(IllegalStateException("No OpenAI API key saved."))

        runCatching {
            val connection = createConnection(apiKey)
            val body = OpenAiChatCompletionRequest(
                model = MODEL,
                temperature = 0.1,
                responseFormat = ResponseFormat(type = "json_object"),
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = "Return valid JSON with a single key named status whose value is ok.",
                    ),
                    ChatMessage(
                        role = "user",
                        content = "Validate that this key can reach the chat completions endpoint.",
                    ),
                ),
            )
            connection.outputStream.use { output ->
                output.writer().use { writer ->
                    writer.write(json.encodeToString(body))
                }
            }
            val payload = connection.readResponseText()
            if (connection.responseCode !in 200..299) {
                error(payload.ifBlank { "OpenAI validation failed with ${connection.responseCode}." })
            }
        }
    }

    private fun createConnection(apiKey: String): HttpURLConnection {
        return (URL("$BASE_URL/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
    }

    private fun buildRequest(
        preferences: AppPreferences,
        request: InsightRequest,
    ): OpenAiChatCompletionRequest {
        val interests = preferences.interests.joinToString { it.label.lowercase() }
        val hints = request.placeContext.hints.joinToString()
        val systemPrompt = buildString {
            appendLine("You are Tournarrat, a grounded travel companion.")
            appendLine("Return strict JSON with keys: title, summary, whyItMatters, confidenceNote, followUps.")
            appendLine("followUps must be an array with 0 to 2 short follow-up prompts.")
            appendLine("Keep the tone ${preferences.tone.name.lowercase().replace('_', ' ')}.")
            appendLine("Prefer concise, factual claims. If context is weak, say so.")
            if (preferences.customPrompt.isNotBlank()) {
                appendLine("User preference: ${preferences.customPrompt.trim()}")
            }
        }
        val userPrompt = buildString {
            appendLine("Create one short place insight in ${preferences.outputLanguage}.")
            appendLine("Mode: ${preferences.mode.name.lowercase()}. Frequency: ${preferences.frequency.name.lowercase()}.")
            appendLine("Interests: $interests.")
            appendLine("Area: ${request.placeContext.areaName}.")
            appendLine("Locality: ${request.placeContext.locality ?: "Unknown"}.")
            appendLine("Country: ${request.placeContext.countryName ?: "Unknown"}.")
            appendLine("Coordinates: ${request.placeContext.latitude}, ${request.placeContext.longitude}.")
            if (hints.isNotBlank()) {
                appendLine("Nearby hints: $hints.")
            }
        }
        return OpenAiChatCompletionRequest(
            model = MODEL,
            temperature = 0.6,
            responseFormat = ResponseFormat(type = "json_object"),
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt),
            ),
        )
    }

    private companion object {
        const val BASE_URL = "https://api.openai.com/v1"
        const val MODEL = "gpt-4.1-mini"
    }
}

private fun HttpURLConnection.readResponseText(): String {
    val stream = if (responseCode in 200..299) inputStream else errorStream
    return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
}

@Serializable
private data class OpenAiChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("response_format")
    val responseFormat: ResponseFormat,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ResponseFormat(
    val type: String,
)

@Serializable
private data class OpenAiChatCompletionResponse(
    val choices: List<Choice>,
) {
    @Serializable
    data class Choice(
        val message: Message,
    )

    @Serializable
    data class Message(
        val content: String,
    )
}
