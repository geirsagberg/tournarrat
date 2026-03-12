package net.sagberg.tournarrat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import net.sagberg.tournarrat.ui.TournarratApp
import net.sagberg.tournarrat.ui.TournarratTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            TournarratTheme {
                TournarratApp()
            }
        }
    }
}
