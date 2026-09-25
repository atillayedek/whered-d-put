package com.wheredidiputit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wheredidiputit.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wdipi_preferences")

/** Signed-in user remembered locally so the app keeps working offline. */
data class CachedUser(val id: String, val email: String)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.dataStore

    val onboardingCompleted: Flow<Boolean> = store.data.map { it[ONBOARDING_DONE] ?: false }

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }

    val cachedUser: Flow<CachedUser?> = store.data.map { prefs ->
        val id = prefs[USER_ID]
        if (id.isNullOrEmpty()) null else CachedUser(id, prefs[USER_EMAIL].orEmpty())
    }

    suspend fun setOnboardingCompleted() {
        store.edit { it[ONBOARDING_DONE] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setCachedUser(user: CachedUser) {
        store.edit {
            it[USER_ID] = user.id
            it[USER_EMAIL] = user.email
        }
    }

    suspend fun currentUser(): CachedUser? = cachedUser.first()

    suspend fun lastPulledAt(userId: String): String? = store.data.first()[pullKey(userId)]

    suspend fun setLastPulledAt(userId: String, isoTimestamp: String) {
        store.edit { it[pullKey(userId)] = isoTimestamp }
    }

    suspend fun setPasswordRecoveryPending(pending: Boolean) {
        store.edit { it[RECOVERY_PENDING] = pending }
    }

    suspend fun isPasswordRecoveryPending(): Boolean = store.data.first()[RECOVERY_PENDING] ?: false

    /** Forgets everything tied to the account; appearance and onboarding stay. */
    suspend fun clearAccountData() {
        store.edit { prefs ->
            val keep = setOf(ONBOARDING_DONE.name, THEME.name)
            prefs.asMap().keys.filter { it.name !in keep }.forEach { prefs -= it }
        }
    }

    private fun pullKey(userId: String) = stringPreferencesKey("last_pulled_at_$userId")

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val THEME = stringPreferencesKey("theme_mode")
        val USER_ID = stringPreferencesKey("cached_user_id")
        val USER_EMAIL = stringPreferencesKey("cached_user_email")
        val RECOVERY_PENDING = booleanPreferencesKey("password_recovery_pending")
    }
}
