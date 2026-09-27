package com.wheredidiputit.data.repository

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.wheredidiputit.core.di.ApplicationScope
import com.wheredidiputit.data.preferences.UserPreferences
import com.wheredidiputit.data.remote.AnnouncementDto
import com.wheredidiputit.data.remote.ConfigRowDto
import com.wheredidiputit.data.remote.SupabaseProvider
import com.wheredidiputit.data.remote.safeCall
import com.wheredidiputit.domain.model.Announcement
import com.wheredidiputit.domain.model.AppSettings
import com.wheredidiputit.domain.model.AuthState
import com.wheredidiputit.domain.repository.AuthRepository
import com.wheredidiputit.domain.repository.RemoteContentRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads what the admin panel publishes (free-plan limit, daily ad cap, the
 * current announcement) when the app comes to the foreground or someone
 * signs in. The last answer is cached, so it also applies offline.
 */
@Singleton
class RemoteContentRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
    private val preferences: UserPreferences,
    authRepository: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : RemoteContentRepository {

    override val settings: StateFlow<AppSettings> =
        preferences.appSettings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    override val announcement: Flow<Announcement?> =
        combine(preferences.announcement, preferences.dismissedAnnouncementId) { current, dismissed ->
            current?.takeUnless { it.id == dismissed }
        }

    private val mutex = Mutex()

    init {
        scope.launch {
            authRepository.authState.filterIsInstance<AuthState.SignedIn>()
                .map { it.userId }
                .distinctUntilChanged()
                .collect { refresh() }
        }
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) refresh() },
            )
        }
    }

    override fun refresh() {
        val client = provider.client ?: return
        if (client.auth.currentSessionOrNull() == null) return
        scope.launch {
            if (!mutex.tryLock()) return@launch
            try {
                safeCall { client.from(TABLE_CONFIG).select().decodeList<ConfigRowDto>() }
                    .onSuccess { rows -> preferences.setAppSettings(rows.toSettings()) }
                // Only an answer from the server may clear the cached announcement.
                safeCall {
                    client.from(TABLE_ANNOUNCEMENTS).select {
                        filter { eq("active", true) }
                        limit(1)
                    }.decodeList<AnnouncementDto>()
                }.onSuccess { rows ->
                    preferences.setAnnouncement(rows.firstOrNull()?.let { Announcement(it.id, it.messageTr, it.messageEn) })
                }
            } finally {
                mutex.unlock()
            }
        }
    }

    override suspend fun dismissAnnouncement(id: String) {
        preferences.setDismissedAnnouncement(id)
    }

    private fun List<ConfigRowDto>.toSettings(): AppSettings {
        val defaults = AppSettings()
        val byKey = associate { it.key to it.value }
        return AppSettings(
            freeItemLimit = byKey["free_item_limit"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..1000 }
                ?: defaults.freeItemLimit,
            adsPerDay = byKey["ads_per_day"]?.jsonPrimitive?.intOrNull?.takeIf { it in 0..10 }
                ?: defaults.adsPerDay,
        )
    }

    private companion object {
        const val TABLE_CONFIG = "app_config"
        const val TABLE_ANNOUNCEMENTS = "app_announcements"
    }
}
