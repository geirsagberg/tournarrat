package net.sagberg.tournarrat

import android.Manifest
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.rule.GrantPermissionRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sagberg.tournarrat.core.data.di.coreDataModule
import net.sagberg.tournarrat.core.data.location.CurrentLocationProvider
import net.sagberg.tournarrat.core.data.narration.Narrator
import net.sagberg.tournarrat.core.data.narration.NarratorDiagnostics
import net.sagberg.tournarrat.core.data.places.PlaceContextProvider
import net.sagberg.tournarrat.core.data.preferences.ApiKeyStore
import net.sagberg.tournarrat.core.data.preferences.InsightHistoryRepository
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepository
import net.sagberg.tournarrat.core.model.AppPreferences
import net.sagberg.tournarrat.core.model.PlaceContext
import net.sagberg.tournarrat.ui.TournarratApp
import net.sagberg.tournarrat.ui.TournarratTheme
import net.sagberg.tournarrat.ui.UiTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module

abstract class BaseSmokeTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() = runBlocking {
        stopKoin()
        startKoin {
            allowOverride(true)
            androidContext(composeRule.activity.applicationContext)
            modules(coreDataModule, appModule, smokeOverrides())
        }
        GlobalContext.get().get<InsightHistoryRepository>().clear()
        GlobalContext.get().get<PreferencesRepository>().update { AppPreferences() }

        composeRule.setContent {
            TournarratTheme {
                TournarratApp()
            }
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    protected abstract fun smokeOverrides(): Module

    protected fun baseOverrides(
        apiKeyStore: ApiKeyStore,
    ): Module = module {
        single<CurrentLocationProvider> { FakeCurrentLocationProvider() }
        single<PlaceContextProvider> { FakePlaceContextProvider() }
        single<Narrator> { NoOpNarrator() }
        single<ApiKeyStore> { apiKeyStore }
    }

    protected fun runCoreFlow(expectDemoFallback: Boolean) {
        composeRule.onNodeWithText("Welcome to Tournarrat").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTags.OnboardingNextButton).performClick()
        composeRule.onNodeWithTag(UiTags.OnboardingNextButton).performClick()
        composeRule.onNodeWithTag(UiTags.OnboardingFinishButton).performClick()

        composeRule.waitUntil(5.seconds.inWholeMilliseconds) {
            composeRule.onAllNodesWithTag(UiTags.HomeScreen).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(UiTags.HomeScreen).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTags.HomeMapHero).assertIsDisplayed()
        composeRule.onAllNodesWithTag(UiTags.HomeDiagnosticsSheet).assertCountEquals(0)
        composeRule.onNodeWithTag(UiTags.HomeModePopups).assertIsSelected()
        composeRule.onNodeWithTag(UiTags.HomeOverflowButton).performClick()
        composeRule.onNodeWithTag(UiTags.HomeDiagnosticsMenuItem).performClick()
        composeRule.onNodeWithTag(UiTags.HomeDiagnosticsSheet).assertIsDisplayed()
        composeRule.waitUntil(5.seconds.inWholeMilliseconds) {
            composeRule.onAllNodesWithText("Default engine").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Speech").assertIsDisplayed()
        composeRule.onNodeWithText("Default engine").assertIsDisplayed()
        composeRule.onNodeWithText("Bound engine").assertIsDisplayed()
        composeRule.onAllNodesWithText("fake.tts").assertCountEquals(3)
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitUntil(5.seconds.inWholeMilliseconds) {
            composeRule.onAllNodesWithTag(UiTags.HomeDiagnosticsSheet).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(UiTags.HomeModeLive).performClick()
        composeRule.onNodeWithTag(UiTags.HomeModeLive).assertIsSelected()
        composeRule.onNodeWithTag(UiTags.HomeGenerateInsightButton).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTags.HomeGenerateInsightButton).performClick()

        composeRule.waitUntil(20.seconds.inWholeMilliseconds) {
            runBlocking {
                GlobalContext.get().get<InsightHistoryRepository>().history.first().isNotEmpty()
            }
        }
        composeRule.onNodeWithTag(UiTags.HomeInsightSheet).assertIsDisplayed()

        if (expectDemoFallback) {
            composeRule.onNodeWithText("A quick read on Test District").assertIsDisplayed()
        } else {
            composeRule.onAllNodesWithText(
                "OpenAI was unavailable, so this insight used demo fallback.",
            ).assertCountEquals(0)
            composeRule.onNodeWithText("Test District").assertIsDisplayed()
        }

        composeRule.onNodeWithTag(UiTags.InsightOpenDetailButton).performClick()
        composeRule.waitUntil(5.seconds.inWholeMilliseconds) {
            composeRule.onAllNodesWithTag(UiTags.DetailScreen).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(UiTags.DetailScreen).assertIsDisplayed()
        composeRule.onNodeWithText("Insight detail").assertIsDisplayed()
        composeRule.onNodeWithText("Test District").assertIsDisplayed()
        composeRule.onNodeWithText("Metadata").performScrollTo().performClick()
        composeRule.onNodeWithText("Confidence").assertIsDisplayed()
        composeRule.onNodeWithText("Tone").assertIsDisplayed()
        composeRule.onNodeWithText("Interests").assertIsDisplayed()
        composeRule.onNodeWithText("Custom prompt").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithTag(UiTags.TabHistory).performClick()
        composeRule.onNodeWithTag(UiTags.HistoryScreen).assertIsDisplayed()
        composeRule.onNodeWithText("Test District").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTags.TabSettings).performClick()
        composeRule.onNodeWithTag(UiTags.SettingsScreen).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTags.SettingsSpeechLocaleField).performScrollTo().assertIsDisplayed()
    }
}

internal class FakeCurrentLocationProvider : CurrentLocationProvider {
    override suspend fun getCurrentLocation(): Result<Location> =
        Result.success(
            Location("smoke-test").apply {
                latitude = 48.2082
                longitude = 16.3738
            },
        )
}

internal class FakePlaceContextProvider : PlaceContextProvider {
    override suspend fun resolve(location: Location): PlaceContext =
        PlaceContext(
            latitude = location.latitude,
            longitude = location.longitude,
            areaName = "Test District",
            fullAddress = "Stephansplatz 1, 1010 Vienna, Austria",
            sourceName = "Fake place provider",
            locality = "Vienna",
            countryName = "Austria",
            hints = listOf("Stephansplatz", "Old Town"),
        )
}

internal class NoOpNarrator : Narrator {
    override suspend fun speak(text: String) = Unit

    override suspend fun diagnostics(): NarratorDiagnostics =
        NarratorDiagnostics(
            defaultEnginePackage = "fake.tts",
            boundEnginePackage = "fake.tts",
            availableEngines = listOf("fake.tts"),
            supportedLocaleTags = listOf("en-US", "nb-NO"),
            selectedLocaleTag = null,
            effectiveLocaleTag = "en-US",
            voiceName = "fake-voice",
            voiceLocale = "en-US",
            isReady = true,
        )

    override fun stop() = Unit
}

internal class InMemoryApiKeyStore(
    initialOpenAiKey: String? = null,
) : ApiKeyStore {
    private var openAiKey: String? = initialOpenAiKey
    private var googlePlacesKey: String? = null

    override fun getOpenAiApiKey(): String? = openAiKey

    override fun setOpenAiApiKey(value: String) {
        openAiKey = value
    }

    override fun clearOpenAiApiKey() {
        openAiKey = null
    }

    override fun getGooglePlacesApiKey(): String? = googlePlacesKey

    override fun setGooglePlacesApiKey(value: String) {
        googlePlacesKey = value
    }

    override fun clearGooglePlacesApiKey() {
        googlePlacesKey = null
    }
}
