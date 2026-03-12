package net.sagberg.tournarrat

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest : BaseSmokeTest() {
    override fun smokeOverrides() = baseOverrides(
        apiKeyStore = InMemoryApiKeyStore(),
    )

    @Test
    fun onboardingManualInsightHistoryAndSettingsFlow() {
        runCoreFlow(expectDemoFallback = true)
    }
}
