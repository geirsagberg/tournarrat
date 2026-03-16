package net.sagberg.tournarrat.core.data.di

import kotlinx.serialization.json.Json
import net.sagberg.tournarrat.core.data.ai.DemoAiClient
import net.sagberg.tournarrat.core.data.ai.OpenAiClient
import net.sagberg.tournarrat.core.data.location.CurrentLocationProvider
import net.sagberg.tournarrat.core.data.location.FusedCurrentLocationProvider
import net.sagberg.tournarrat.core.data.narration.AndroidNarrator
import net.sagberg.tournarrat.core.data.narration.Narrator
import net.sagberg.tournarrat.core.data.places.GooglePlacesContextProvider
import net.sagberg.tournarrat.core.data.places.PlaceContextProvider
import net.sagberg.tournarrat.core.data.preferences.ApiKeyStore
import net.sagberg.tournarrat.core.data.preferences.EncryptedApiKeyStore
import net.sagberg.tournarrat.core.data.preferences.InsightHistoryRepository
import net.sagberg.tournarrat.core.data.preferences.InsightHistoryRepositoryImpl
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepository
import net.sagberg.tournarrat.core.data.preferences.PreferencesRepositoryImpl
import net.sagberg.tournarrat.core.data.repository.InsightService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreDataModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    single<ApiKeyStore> { EncryptedApiKeyStore(androidContext()) }
    single<PreferencesRepository> { PreferencesRepositoryImpl(androidContext(), get()) }
    single<InsightHistoryRepository> { InsightHistoryRepositoryImpl(androidContext(), get()) }
    single<CurrentLocationProvider> { FusedCurrentLocationProvider(androidContext()) }
    single<PlaceContextProvider> { GooglePlacesContextProvider(androidContext(), get(), get()) }
    single<Narrator> { AndroidNarrator(androidContext(), get()) }
    single { OpenAiClient(get(), get()) }
    single { DemoAiClient() }
    single { InsightService(get(), get(), get(), get(), get(), get()) }
}
