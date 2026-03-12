package net.sagberg.tournarrat

import android.Manifest
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val smokeTestModule = module {
        single<CurrentLocationProvider> { FakeCurrentLocationProvider() }
        single<PlaceContextProvider> { FakePlaceContextProvider() }
        single<Narrator> { NoOpNarrator() }
        single<ApiKeyStore> { InMemoryApiKeyStore() }
    }

    @Before
    fun setUp() = runBlocking {
        stopKoin()
        startKoin {
            allowOverride(true)
            androidContext(composeRule.activity.applicationContext)
            modules(coreDataModule, appModule, smokeTestModule)
        }
        GlobalContext.get().get<ApiKeyStore>().clearOpenAiApiKey()
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

    @Test
    fun onboardingManualInsightHistoryAndSettingsFlow() {
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

        composeRule.waitUntil(10.seconds.inWholeMilliseconds) {
            composeRule.onAllNodesWithTag(UiTags.InsightCard).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("A quick read on Test District").assertIsDisplayed()
        composeRule.onNodeWithText("OpenAI was unavailable, so this insight used demo fallback.").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTags.InsightOpenDetailButton).performClick()
        composeRule.onNodeWithTag(UiTags.DetailScreen).assertIsDisplayed()
        composeRule.onNodeWithText("Insight detail").assertIsDisplayed()
        composeRule.onNodeWithText("Test District").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithTag(UiTags.TabHistory).performClick()
        composeRule.onNodeWithTag(UiTags.HistoryScreen).assertIsDisplayed()
        composeRule.onNodeWithText("A quick read on Test District").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTags.TabSettings).performClick()
        composeRule.onNodeWithTag(UiTags.SettingsScreen).assertIsDisplayed()
    }
}

private class FakeCurrentLocationProvider : CurrentLocationProvider {
    override suspend fun getCurrentLocation(): Location =
        Location("smoke-test").apply {
            latitude = 48.2082
            longitude = 16.3738
        }
}

private class FakePlaceContextProvider : PlaceContextProvider {
    override suspend fun resolve(location: Location): PlaceContext =
        PlaceContext(
            latitude = location.latitude,
            longitude = location.longitude,
            areaName = "Test District",
            locality = "Vienna",
            countryName = "Austria",
            hints = listOf("Stephansplatz", "Old Town"),
        )
}

private class NoOpNarrator : Narrator {
    override suspend fun speak(text: String) = Unit

    override fun stop() = Unit
}

private class InMemoryApiKeyStore : ApiKeyStore {
    private var openAiKey: String? = null

    override fun getOpenAiApiKey(): String? = openAiKey

    override fun setOpenAiApiKey(value: String) {
        openAiKey = value
    }

    override fun clearOpenAiApiKey() {
        openAiKey = null
    }
}
