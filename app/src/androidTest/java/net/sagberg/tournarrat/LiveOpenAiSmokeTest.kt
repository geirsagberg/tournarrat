package net.sagberg.tournarrat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveOpenAiSmokeTest : BaseSmokeTest() {
    private val liveOpenAiKey: String by lazy {
        InstrumentationRegistry.getArguments().getString("openAiApiKey").orEmpty().trim()
    }

    override fun smokeOverrides() = baseOverrides(
        apiKeyStore = InMemoryApiKeyStore(liveOpenAiKey),
    )

    @Test
    fun onboardingManualInsightHistoryAndSettingsFlowWithLiveOpenAi() {
        assumeTrue(
            "Skipping live OpenAI smoke test because no openAiApiKey instrumentation argument was provided.",
            liveOpenAiKey.isNotBlank(),
        )
        runCoreFlow(expectDemoFallback = false)
    }
}
