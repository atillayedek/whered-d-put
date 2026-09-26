package com.wheredidiputit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wheredidiputit.domain.ads.AdHistory
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
            // Ad frequency is per device, so signing out can't be used to see more ads.
            // Premium belongs to the Google Play account on this device, not to the app account.
            val keep = setOf(ONBOARDING_DONE.name, THEME.name, AD_LAST_SHOWN.name, AD_DAY.name, AD_COUNT.name, PREMIUM_CACHED.name)
            prefs.asMap().keys.filter { it.name !in keep }.forEach { prefs -= it }
        }
    }

    suspend fun adHistory(): AdHistory {
        val prefs = store.data.first()
        return AdHistory(
            lastShownAt = prefs[AD_LAST_SHOWN],
            day = prefs[AD_DAY],
            shownToday = prefs[AD_COUNT] ?: 0,
        )
    }

    suspend fun setAdHistory(history: AdHistory) {
        store.edit { prefs ->
            history.lastShownAt?.let { prefs[AD_LAST_SHOWN] = it }
            history.day?.let { prefs[AD_DAY] = it }
            prefs[AD_COUNT] = history.shownToday
        }
    }

    /** Last answer from Google Play, used until Play answers again (offline, cold start). */
    suspend fun premiumCached(): Boolean = store.data.first()[PREMIUM_CACHED] ?: false

    suspend fun setPremiumCached(premium: Boolean) {
        store.edit { it[PREMIUM_CACHED] = premium }
    }

    private fun pullKey(userId: String) = stringPreferencesKey("last_pulled_at_$userId")

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val THEME = stringPreferencesKey("theme_mode")
        val USER_ID = stringPreferencesKey("cached_user_id")
        val USER_EMAIL = stringPreferencesKey("cached_user_email")
        val RECOVERY_PENDING = booleanPreferencesKey("password_recovery_pending")
        val AD_LAST_SHOWN = longPreferencesKey("ad_last_shown_at")
        val AD_DAY = stringPreferencesKey("ad_day")
        val AD_COUNT = intPreferencesKey("ad_shown_today")
        val PREMIUM_CACHED = booleanPreferencesKey("premium_cached")
    }
}
