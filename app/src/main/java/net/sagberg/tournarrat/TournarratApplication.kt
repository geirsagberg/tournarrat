package net.sagberg.tournarrat

import android.app.Application
import net.sagberg.tournarrat.appModule
import net.sagberg.tournarrat.core.data.di.coreDataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TournarratApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TournarratApplication)
            modules(coreDataModule, appModule)
        }
    }
}
