package net.sagberg.tournarrat.core.data.narration

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

interface Narrator {
    suspend fun speak(text: String)

    fun stop()
}

class AndroidNarrator(
    context: Context,
) : Narrator {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var ready = false

    override suspend fun speak(text: String) {
        ensureReady()
        if (ready) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tournarrat")
        }
    }

    override fun stop() {
        textToSpeech?.stop()
    }

    private suspend fun ensureReady() {
        if (ready && textToSpeech != null) return
        ready = suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(appContext) { status ->
                textToSpeech = engine
                if (status == TextToSpeech.SUCCESS) {
                    engine?.language = Locale.getDefault()
                    continuation.resume(true)
                } else {
                    continuation.resume(false)
                }
            }
        }
    }
}
