package com.wheredidiputit

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheredidiputit.core.designsystem.theme.WdipiTheme
import com.wheredidiputit.data.ads.AdsManager
import com.wheredidiputit.domain.model.ThemeMode
import com.wheredidiputit.presentation.app.MainViewModel
import com.wheredidiputit.presentation.app.RootDestination
import com.wheredidiputit.presentation.app.WdipiApp
import com.wheredidiputit.presentation.common.AdsController
import com.wheredidiputit.presentation.common.LocalAdsController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var adsManager: AdsManager

    private val adsController = object : AdsController {
        override val privacyOptionsRequired: StateFlow<Boolean> get() = adsManager.privacyOptionsRequired
        override fun onNaturalBreak() = adsManager.onNaturalBreak(this@MainActivity)
        override fun openPrivacyOptions() = adsManager.showPrivacyOptions(this@MainActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value.isLoading }
        if (savedInstanceState == null) handleAuthLink(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // Ask for ad consent (where required) only once the person is inside the app.
            LaunchedEffect(state.destination) {
                if (state.destination == RootDestination.MAIN) adsManager.gatherConsent(this@MainActivity)
            }
            CompositionLocalProvider(LocalAdsController provides adsController) {
                WdipiTheme(themeMode = state.themeMode) {
                    WdipiApp(state = state, viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthLink(intent)
    }

    private fun handleAuthLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.toString()?.let(viewModel::onAuthLink)
    }
}
