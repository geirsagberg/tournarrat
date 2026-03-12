package net.sagberg.tournarrat.core.data.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.sagberg.tournarrat.core.data.preferences.ApiKeyStore
import net.sagberg.tournarrat.core.model.AppPreferences
import net.sagberg.tournarrat.core.model.InsightDraft
import net.sagberg.tournarrat.core.model.InsightRequest

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

            val response = json.decodeFromString<OpenAiResponsesResponse>(payload)
            val content = response.firstOutputText()
                ?: response.firstRefusal()
                ?: error("OpenAI response did not contain assistant content.")
            json.decodeFromString<InsightDraft>(content)
        }
    }

    override suspend fun validateCurrentKey(): Result<Unit> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getOpenAiApiKey()
            ?: return@withContext Result.failure(IllegalStateException("No OpenAI API key saved."))

        runCatching {
            val connection = createConnection(apiKey)
            val body = OpenAiResponsesRequest(
                model = MODEL,
                store = false,
                input = listOf(
                    message(
                        role = "system",
                        text = "You are validating OpenAI API access for an Android app.",
                    ),
                    message(
                        role = "user",
                        text = "Reply with the exact text ok.",
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
        return (URL("$BASE_URL/responses").openConnection() as HttpURLConnection).apply {
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
    ): OpenAiResponsesRequest {
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
        return OpenAiResponsesRequest(
            model = MODEL,
            store = false,
            input = listOf(
                message(role = "system", text = systemPrompt),
                message(role = "user", text = userPrompt),
            ),
            text = ResponseTextConfig(
                format = ResponseTextFormat(
                    type = "json_schema",
                    name = "insight_draft",
                    schema = insightDraftSchema(),
                    strict = true,
                ),
            ),
        )
    }

    private companion object {
        const val BASE_URL = "https://api.openai.com/v1"
        const val MODEL = "gpt-5-mini"
    }
}

private fun message(
    role: String,
    text: String,
): ResponseInputItem = ResponseInputItem(
    role = role,
    content = listOf(ResponseInputContent(text = text)),
)

private fun insightDraftSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("title") {
            put("type", "string")
        }
        putJsonObject("summary") {
            put("type", "string")
        }
        putJsonObject("whyItMatters") {
            put("type", "string")
        }
        putJsonObject("confidenceNote") {
            put("type", "string")
        }
        putJsonObject("followUps") {
            put("type", "array")
            putJsonObject("items") {
                put("type", "string")
            }
            put("maxItems", 2)
        }
    }
    putJsonArray("required") {
        add(JsonPrimitive("title"))
        add(JsonPrimitive("summary"))
        add(JsonPrimitive("whyItMatters"))
        add(JsonPrimitive("confidenceNote"))
        add(JsonPrimitive("followUps"))
    }
    put("additionalProperties", false)
}

private fun HttpURLConnection.readResponseText(): String {
    val stream = if (responseCode in 200..299) inputStream else errorStream
    return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
}

@Serializable
private data class OpenAiResponsesRequest(
    val model: String,
    val store: Boolean,
    val input: List<ResponseInputItem>,
    val text: ResponseTextConfig? = null,
)

@Serializable
private data class ResponseInputItem(
    val role: String,
    val content: List<ResponseInputContent>,
)

@Serializable
private data class ResponseInputContent(
    val type: String = "input_text",
    val text: String,
)

@Serializable
private data class ResponseTextConfig(
    val format: ResponseTextFormat,
)

@Serializable
private data class ResponseTextFormat(
    val type: String,
    val name: String? = null,
    val schema: JsonElement? = null,
    val strict: Boolean? = null,
)

@Serializable
private data class OpenAiResponsesResponse(
    val output: List<ResponseOutputItem> = emptyList(),
)

@Serializable
private data class ResponseOutputItem(
    val content: List<ResponseOutputContent> = emptyList(),
)

@Serializable
private data class ResponseOutputContent(
    val text: String? = null,
    val refusal: String? = null,
)

private fun OpenAiResponsesResponse.firstOutputText(): String? =
    output.asSequence()
        .flatMap { it.content.asSequence() }
        .mapNotNull(ResponseOutputContent::text)
        .firstOrNull(String::isNotBlank)

private fun OpenAiResponsesResponse.firstRefusal(): String? =
    output.asSequence()
        .flatMap { it.content.asSequence() }
        .mapNotNull(ResponseOutputContent::refusal)
        .firstOrNull(String::isNotBlank)
