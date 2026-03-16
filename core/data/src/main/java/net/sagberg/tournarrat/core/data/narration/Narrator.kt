package net.sagberg.tournarrat.core.data.narration

import android.content.Context
import android.provider.Settings
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepository

data class NarratorDiagnostics(
    val defaultEnginePackage: String? = null,
    val boundEnginePackage: String? = null,
    val availableEngines: List<String> = emptyList(),
    val supportedLocaleTags: List<String> = emptyList(),
    val selectedLocaleTag: String? = null,
    val effectiveLocaleTag: String? = null,
    val voiceName: String? = null,
    val voiceLocale: String? = null,
    val isReady: Boolean = false,
)

interface Narrator {
    suspend fun speak(text: String)

    suspend fun diagnostics(): NarratorDiagnostics

    fun stop()
}

class AndroidNarrator(
    context: Context,
    private val preferencesRepository: PreferencesRepository,
) : Narrator {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var boundEnginePackage: String? = null
    private var ready = false

    override suspend fun speak(text: String) {
        ensureReady()
        if (ready) {
            configureLocale()
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tournarrat")
        }
    }

    override suspend fun diagnostics(): NarratorDiagnostics {
        ensureReady()
        val engine = textToSpeech
        configureLocale()
        val voice = engine?.voice
        val supportedLocaleTags = engine.supportedLocaleTags()
        val selectedLocaleTag = preferencesRepository.preferences.first().ttsLocaleTag
        return NarratorDiagnostics(
            defaultEnginePackage = Settings.Secure.getString(
                appContext.contentResolver,
                "tts_default_synth",
            ),
            boundEnginePackage = boundEnginePackage,
            availableEngines = engine?.engines?.map { it.name }.orEmpty(),
            supportedLocaleTags = supportedLocaleTags,
            selectedLocaleTag = selectedLocaleTag,
            effectiveLocaleTag = voice?.locale?.toLanguageTag()
                ?: supportedLocaleTags.firstOrNull(),
            voiceName = voice?.name,
            voiceLocale = voice?.locale?.toLanguageTag(),
            isReady = ready,
        )
    }

    override fun stop() {
        textToSpeech?.stop()
    }

    private suspend fun ensureReady() {
        if (ready && textToSpeech != null) return
        ready = suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            val requestedEnginePackage = Settings.Secure.getString(
                appContext.contentResolver,
                "tts_default_synth",
            )?.takeIf { it.isNotBlank() }
            val onInit: (Int) -> Unit = { status ->
                textToSpeech = engine
                if (status == TextToSpeech.SUCCESS) {
                    boundEnginePackage = requestedEnginePackage ?: engine?.defaultEngine
                    continuation.resume(true)
                } else {
                    continuation.resume(false)
                }
            }
            engine = if (requestedEnginePackage != null) {
                TextToSpeech(appContext, onInit, requestedEnginePackage)
            } else {
                TextToSpeech(appContext, onInit)
            }
        }
        if (ready) {
            configureLocale()
        }
    }

    private suspend fun configureLocale() {
        val engine = textToSpeech ?: return
        val supportedLocaleTags = engine.supportedLocaleTags()
        val preferences = preferencesRepository.preferences.first()
        val preferredTag = preferences.ttsLocaleTag
        val localeTag = when {
            preferredTag != null && preferredTag in supportedLocaleTags -> preferredTag
            engine.voice?.locale != null -> engine.voice?.locale?.toLanguageTag()
            supportedLocaleTags.isNotEmpty() -> supportedLocaleTags.first()
            else -> Locale.getDefault().toLanguageTag()
        } ?: Locale.getDefault().toLanguageTag()
        engine.language = Locale.forLanguageTag(localeTag)
    }
}

private fun TextToSpeech?.supportedLocaleTags(): List<String> {
    val engine = this ?: return emptyList()
    return engine.voices.orEmpty()
        .mapNotNull { it.locale?.toLanguageTag() }
        .distinct()
        .sorted()
}
