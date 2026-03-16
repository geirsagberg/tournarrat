package net.sagberg.tournarrat

import android.Manifest
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.rule.GrantPermissionRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import net.sagberg.tournarrat.core.data.di.coreDataModule
import net.sagberg.tournarrat.core.data.location.CurrentLocationProvider
import net.sagberg.tournarrat.core.data.narration.Narrator
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
        composeRule.onNodeWithTag(UiTags.HomeGenerateInsightButton).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTags.HomeGenerateInsightButton).performClick()

        composeRule.waitUntil(20.seconds.inWholeMilliseconds) {
            composeRule.onAllNodesWithTag(UiTags.InsightCard).fetchSemanticsNodes().isNotEmpty()
        }

        if (expectDemoFallback) {
            composeRule.onNodeWithText("A quick read on Test District").assertIsDisplayed()
        } else {
            composeRule.onAllNodesWithText(
                "OpenAI was unavailable, so this insight used demo fallback.",
            ).assertCountEquals(0)
            composeRule.onNodeWithText("Test District").assertIsDisplayed()
        }

        composeRule.onNodeWithTag(UiTags.InsightOpenDetailButton).performScrollTo().performClick()
        composeRule.waitUntil(5.seconds.inWholeMilliseconds) {
            composeRule.onAllNodesWithTag(UiTags.DetailScreen).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(UiTags.DetailScreen).assertIsDisplayed()
        composeRule.onNodeWithText("Insight detail").assertIsDisplayed()
        composeRule.onNodeWithText("Test District").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithTag(UiTags.TabHistory).performClick()
        composeRule.onNodeWithTag(UiTags.HistoryScreen).assertIsDisplayed()
        composeRule.onNodeWithText("Test District").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTags.TabSettings).performClick()
        composeRule.onNodeWithTag(UiTags.SettingsScreen).assertIsDisplayed()
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
            locality = "Vienna",
            countryName = "Austria",
            hints = listOf("Stephansplatz", "Old Town"),
        )
}

internal class NoOpNarrator : Narrator {
    override suspend fun speak(text: String) = Unit

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
