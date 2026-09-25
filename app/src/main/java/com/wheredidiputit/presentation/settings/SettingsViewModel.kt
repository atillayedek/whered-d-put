package com.wheredidiputit.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheredidiputit.BuildConfig
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.AppResult
import com.wheredidiputit.domain.model.AuthState
import com.wheredidiputit.domain.model.ThemeMode
import com.wheredidiputit.domain.repository.AccountRepository
import com.wheredidiputit.domain.repository.AuthRepository
import com.wheredidiputit.domain.repository.ItemRepository
import com.wheredidiputit.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingsDialog {
    /** [unsyncedCount] memories would be lost from this device. */
    data class SignOut(val unsyncedCount: Int) : SettingsDialog
    data object DeleteAccount : SettingsDialog
}

data class SettingsUiState(
    val email: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dialog: SettingsDialog? = null,
    val isWorking: Boolean = false,
) {
    val versionLabel: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(local, authRepository.authState, settingsRepository.themeMode) { state, auth, theme ->
        state.copy(email = (auth as? AuthState.SignedIn)?.email.orEmpty(), themeMode = theme)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val errorChannel = Channel<AppError>(Channel.BUFFERED)
    val errors: Flow<AppError> = errorChannel.receiveAsFlow()

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun requestSignOut() {
        viewModelScope.launch {
            val unsynced = itemRepository.unsyncedCount()
            local.update { it.copy(dialog = SettingsDialog.SignOut(unsynced)) }
        }
    }

    fun requestDeleteAccount() {
        local.update { it.copy(dialog = SettingsDialog.DeleteAccount) }
    }

    fun dismissDialog() {
        if (local.value.isWorking) return
        local.update { it.copy(dialog = null) }
    }

    fun confirmSignOut() {
        local.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            accountRepository.signOut()
            local.update { it.copy(isWorking = false, dialog = null) }
        }
    }

    fun confirmDeleteAccount() {
        local.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val result = accountRepository.deleteAccount()
            local.update { it.copy(isWorking = false, dialog = null) }
            if (result is AppResult.Failure) errorChannel.send(result.error)
        }
    }
}
