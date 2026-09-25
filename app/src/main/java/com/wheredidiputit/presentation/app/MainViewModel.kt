package com.wheredidiputit.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheredidiputit.data.sync.SyncScheduler
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.AuthRedirectResult
import com.wheredidiputit.domain.model.AuthState
import com.wheredidiputit.domain.model.ThemeMode
import com.wheredidiputit.domain.repository.AuthRepository
import com.wheredidiputit.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RootDestination { LOADING, SETUP_REQUIRED, ONBOARDING, AUTH, PASSWORD_RECOVERY, MAIN }

data class AppUiState(
    val destination: RootDestination = RootDestination.LOADING,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    val isLoading: Boolean get() = destination == RootDestination.LOADING
}

enum class AppMessage { EMAIL_CONFIRMED, LINK_EXPIRED, NETWORK_ERROR, GENERIC_ERROR, PASSWORD_UPDATED }

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    syncScheduler: SyncScheduler,
) : ViewModel() {

    private val passwordRecovery = MutableStateFlow(false)
    private val messageChannel = Channel<AppMessage>(Channel.BUFFERED)
    val messages: Flow<AppMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.onboardingCompleted,
        authRepository.authState,
        settingsRepository.themeMode,
        passwordRecovery,
    ) { onboarded, auth, theme, recovering ->
        val destination = when {
            !authRepository.isBackendConfigured -> RootDestination.SETUP_REQUIRED
            auth is AuthState.Loading -> RootDestination.LOADING
            auth is AuthState.SignedIn && recovering -> RootDestination.PASSWORD_RECOVERY
            auth is AuthState.SignedIn -> RootDestination.MAIN
            !onboarded -> RootDestination.ONBOARDING
            else -> RootDestination.AUTH
        }
        AppUiState(destination = destination, themeMode = theme)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        viewModelScope.launch {
            authRepository.authState
                .map { (it as? AuthState.SignedIn)?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        syncScheduler.schedulePeriodicSync()
                        syncScheduler.requestSync()
                    }
                }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted() }
    }

    fun onAuthLink(url: String) {
        viewModelScope.launch {
            when (val result = authRepository.handleAuthRedirect(url)) {
                AuthRedirectResult.NotAnAuthLink -> Unit
                AuthRedirectResult.SignedIn -> {
                    settingsRepository.setOnboardingCompleted()
                    messageChannel.send(AppMessage.EMAIL_CONFIRMED)
                }
                AuthRedirectResult.PasswordRecovery -> passwordRecovery.value = true
                AuthRedirectResult.LinkExpired -> messageChannel.send(AppMessage.LINK_EXPIRED)
                is AuthRedirectResult.Failed -> messageChannel.send(
                    if (result.error == AppError.NETWORK) AppMessage.NETWORK_ERROR else AppMessage.GENERIC_ERROR,
                )
            }
        }
    }

    fun onPasswordRecoveryFinished(updated: Boolean) {
        passwordRecovery.value = false
        if (updated) viewModelScope.launch { messageChannel.send(AppMessage.PASSWORD_UPDATED) }
    }
}
