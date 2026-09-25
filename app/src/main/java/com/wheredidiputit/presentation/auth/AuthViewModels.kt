package com.wheredidiputit.presentation.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.AppResult
import com.wheredidiputit.domain.model.SignUpResult
import com.wheredidiputit.domain.repository.AuthRepository
import com.wheredidiputit.presentation.navigation.SignInRoute
import com.wheredidiputit.presentation.navigation.VerifyEmailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal const val MIN_PASSWORD_LENGTH = 8

internal fun String.isValidEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(trim()).matches()

@HiltViewModel
class SignInViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
) : ViewModel() {
    var email by mutableStateOf(savedStateHandle.toRoute<SignInRoute>().email.orEmpty())
        private set
    var password by mutableStateOf("")
        private set
    var emailInvalid by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun onEmailChange(value: String) {
        email = value
        emailInvalid = false
        error = null
    }

    fun onPasswordChange(value: String) {
        password = value
        error = null
    }

    fun signIn() {
        if (isLoading) return
        if (!email.isValidEmail()) {
            emailInvalid = true
            return
        }
        if (password.isEmpty()) {
            error = AppError.INVALID_CREDENTIALS
            return
        }
        isLoading = true
        viewModelScope.launch {
            val result = authRepository.signIn(email, password)
            isLoading = false
            // On success the app switches to the signed-in experience automatically.
            if (result is AppResult.Failure) error = result.error
        }
    }
}

sealed interface SignUpEvent {
    data class VerificationRequired(val email: String) : SignUpEvent
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var emailInvalid by mutableStateOf(false)
        private set
    var passwordTooShort by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    private val eventChannel = Channel<SignUpEvent>(Channel.BUFFERED)
    val events: Flow<SignUpEvent> = eventChannel.receiveAsFlow()

    fun onEmailChange(value: String) {
        email = value
        emailInvalid = false
        error = null
    }

    fun onPasswordChange(value: String) {
        password = value
        passwordTooShort = false
        error = null
    }

    fun signUp() {
        if (isLoading) return
        emailInvalid = !email.isValidEmail()
        passwordTooShort = password.length < MIN_PASSWORD_LENGTH
        if (emailInvalid || passwordTooShort) return
        isLoading = true
        viewModelScope.launch {
            val result = authRepository.signUp(email, password)
            isLoading = false
            when (result) {
                is AppResult.Success -> if (result.value == SignUpResult.VERIFICATION_REQUIRED) {
                    eventChannel.send(SignUpEvent.VerificationRequired(email.trim()))
                }
                is AppResult.Failure -> error = result.error
            }
        }
    }
}

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
) : ViewModel() {
    val email: String = savedStateHandle.toRoute<VerifyEmailRoute>().email

    var cooldownSeconds by mutableIntStateOf(RESEND_COOLDOWN_SECONDS)
        private set
    var isSending by mutableStateOf(false)
        private set
    var resent by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set

    private var cooldownJob: Job? = null

    init {
        startCooldown()
    }

    fun resend() {
        if (isSending || cooldownSeconds > 0) return
        isSending = true
        error = null
        viewModelScope.launch {
            when (val result = authRepository.resendVerificationEmail(email)) {
                is AppResult.Success -> {
                    resent = true
                    startCooldown()
                }
                is AppResult.Failure -> error = result.error
            }
            isSending = false
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownSeconds = RESEND_COOLDOWN_SECONDS
        cooldownJob = viewModelScope.launch {
            while (cooldownSeconds > 0) {
                delay(1_000)
                cooldownSeconds -= 1
            }
        }
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    var email by mutableStateOf("")
        private set
    var emailInvalid by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var sent by mutableStateOf(false)
        private set

    fun onEmailChange(value: String) {
        email = value
        emailInvalid = false
        error = null
    }

    fun send() {
        if (isLoading) return
        if (!email.isValidEmail()) {
            emailInvalid = true
            return
        }
        isLoading = true
        viewModelScope.launch {
            when (val result = authRepository.sendPasswordReset(email)) {
                is AppResult.Success -> sent = true
                is AppResult.Failure -> error = result.error
            }
            isLoading = false
        }
    }
}

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    var password by mutableStateOf("")
        private set
    var passwordTooShort by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    private val doneChannel = Channel<Unit>(Channel.CONFLATED)
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    fun onPasswordChange(value: String) {
        password = value
        passwordTooShort = false
        error = null
    }

    fun save() {
        if (isLoading) return
        if (password.length < MIN_PASSWORD_LENGTH) {
            passwordTooShort = true
            return
        }
        isLoading = true
        viewModelScope.launch {
            when (val result = authRepository.updatePassword(password)) {
                is AppResult.Success -> doneChannel.send(Unit)
                is AppResult.Failure -> error = result.error
            }
            isLoading = false
        }
    }
}
