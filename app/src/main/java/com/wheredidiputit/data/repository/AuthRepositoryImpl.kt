package com.wheredidiputit.data.repository

import android.net.Uri
import com.wheredidiputit.core.config.AppConfig
import com.wheredidiputit.core.di.ApplicationScope
import com.wheredidiputit.data.preferences.CachedUser
import com.wheredidiputit.data.preferences.UserPreferences
import com.wheredidiputit.data.remote.SupabaseProvider
import com.wheredidiputit.data.remote.safeCall
import com.wheredidiputit.data.remote.toAppError
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.AppResult
import com.wheredidiputit.domain.model.AuthRedirectResult
import com.wheredidiputit.domain.model.AuthState
import com.wheredidiputit.domain.model.SignUpResult
import com.wheredidiputit.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
    private val preferences: UserPreferences,
    @ApplicationScope private val scope: CoroutineScope,
) : AuthRepository {

    override val isBackendConfigured: Boolean = provider.client != null

    override val authState: StateFlow<AuthState> = provider.client?.let { client ->
        combine(client.auth.sessionStatus, preferences.cachedUser) { status, cached ->
            status.toAuthState(cached)
        }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)
    } ?: MutableStateFlow<AuthState>(AuthState.SignedOut).asStateFlow()

    init {
        provider.client?.let { client ->
            scope.launch {
                client.auth.sessionStatus.collect { status ->
                    val user = (status as? SessionStatus.Authenticated)?.session?.user ?: return@collect
                    preferences.setCachedUser(CachedUser(user.id, user.email.orEmpty()))
                }
            }
        }
    }

    /**
     * Offline-first: when the access token cannot be refreshed because the
     * device is offline, the person stays signed in with their local data.
     */
    private fun SessionStatus.toAuthState(cached: CachedUser?): AuthState = when (this) {
        is SessionStatus.Authenticated -> session.user?.let { AuthState.SignedIn(it.id, it.email.orEmpty()) }
            ?: cached?.toSignedIn()
            ?: AuthState.Loading
        is SessionStatus.NotAuthenticated -> AuthState.SignedOut
        is SessionStatus.RefreshFailure -> cached?.toSignedIn() ?: AuthState.SignedOut
        else -> AuthState.Loading
    }

    private fun CachedUser.toSignedIn() = AuthState.SignedIn(id, email)

    override suspend fun signIn(email: String, password: String): AppResult<Unit> = call { client ->
        client.auth.signInWith(Email) {
            this.email = email.normalizedEmail()
            this.password = password
        }
        preferences.setPasswordRecoveryPending(false)
    }

    override suspend fun signUp(email: String, password: String): AppResult<SignUpResult> = call { client ->
        client.auth.signUpWith(Email, redirectUrl = AppConfig.AUTH_REDIRECT_URL) {
            this.email = email.normalizedEmail()
            this.password = password
        }
        if (client.auth.currentSessionOrNull() != null) SignUpResult.SIGNED_IN else SignUpResult.VERIFICATION_REQUIRED
    }

    override suspend fun resendVerificationEmail(email: String): AppResult<Unit> = call { client ->
        client.auth.resendEmail(OtpType.Email.SIGNUP, email.normalizedEmail())
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = call { client ->
        preferences.setPasswordRecoveryPending(true)
        client.auth.resetPasswordForEmail(
            email = email.normalizedEmail(),
            redirectUrl = AppConfig.PASSWORD_RECOVERY_REDIRECT_URL,
        )
    }

    override suspend fun updatePassword(newPassword: String): AppResult<Unit> = call { client ->
        client.auth.updateUser { password = newPassword }
        preferences.setPasswordRecoveryPending(false)
    }

    override suspend fun handleAuthRedirect(url: String): AuthRedirectResult {
        val client = provider.client ?: return AuthRedirectResult.NotAnAuthLink
        val uri = Uri.parse(url)
        if (uri.scheme != AppConfig.AUTH_SCHEME || uri.host != AppConfig.AUTH_HOST) {
            return AuthRedirectResult.NotAnAuthLink
        }
        val params = uri.allParameters()

        if (params.containsKey("error") || params.containsKey("error_code")) {
            val description = (params["error_code"].orEmpty() + " " + params["error_description"].orEmpty()).lowercase()
            return if ("expired" in description || "invalid" in description) {
                AuthRedirectResult.LinkExpired
            } else {
                AuthRedirectResult.Failed(AppError.UNKNOWN)
            }
        }

        val code = params["code"] ?: return AuthRedirectResult.NotAnAuthLink
        val isRecovery = params["type"] == "recovery" || preferences.isPasswordRecoveryPending()

        return safeCall { client.auth.exchangeCodeForSession(code) }.fold(
            onSuccess = {
                if (isRecovery) AuthRedirectResult.PasswordRecovery else AuthRedirectResult.SignedIn
            },
            onFailure = { error ->
                when (error) {
                    is HttpRequestException, is IOException -> AuthRedirectResult.Failed(AppError.NETWORK)
                    // Expired/used codes, or a link opened on a different device than the one that asked for it.
                    is RestException, is IllegalArgumentException -> AuthRedirectResult.LinkExpired
                    else -> AuthRedirectResult.Failed(error.toAppError())
                }
            },
        )
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    private fun String.normalizedEmail() = trim().lowercase(Locale.ROOT)

    /** Query and fragment parameters (implicit-flow errors arrive in the fragment). */
    private fun Uri.allParameters(): Map<String, String> = buildMap {
        queryParameterNames.forEach { name -> getQueryParameter(name)?.let { put(name, it) } }
        encodedFragment?.split('&')?.forEach { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) put(Uri.decode(parts[0]), Uri.decode(parts[1]))
        }
    }
}
