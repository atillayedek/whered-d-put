package com.wheredidiputit.presentation.app

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wheredidiputit.R
import com.wheredidiputit.presentation.auth.AuthNavHost
import com.wheredidiputit.presentation.auth.ResetPasswordScreen
import com.wheredidiputit.presentation.common.LocalSnackbarHostState
import com.wheredidiputit.presentation.navigation.MainNavHost
import com.wheredidiputit.presentation.onboarding.OnboardingScreen
import com.wheredidiputit.presentation.onboarding.SetupRequiredScreen

@Composable
fun WdipiApp(state: AppUiState, viewModel: MainViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(context.getString(message.textRes()))
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Crossfade(targetState = state.destination, animationSpec = tween(220), label = "root") { destination ->
                when (destination) {
                    RootDestination.LOADING -> Box(Modifier.fillMaxSize())
                    RootDestination.SETUP_REQUIRED -> SetupRequiredScreen()
                    RootDestination.ONBOARDING -> OnboardingScreen(onGetStarted = viewModel::completeOnboarding)
                    RootDestination.AUTH -> AuthNavHost()
                    RootDestination.PASSWORD_RECOVERY -> ResetPasswordScreen(onFinished = viewModel::onPasswordRecoveryFinished)
                    RootDestination.MAIN -> MainNavHost()
                }
            }
        }
    }
}

@StringRes
private fun AppMessage.textRes(): Int = when (this) {
    AppMessage.EMAIL_CONFIRMED -> R.string.message_email_confirmed
    AppMessage.LINK_EXPIRED -> R.string.message_link_expired
    AppMessage.NETWORK_ERROR -> R.string.error_network
    AppMessage.GENERIC_ERROR -> R.string.error_generic
    AppMessage.PASSWORD_UPDATED -> R.string.message_password_updated
}
