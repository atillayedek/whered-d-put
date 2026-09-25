package com.wheredidiputit.data.repository

import com.wheredidiputit.core.config.AppConfig
import com.wheredidiputit.core.di.IoDispatcher
import com.wheredidiputit.data.image.ImageStore
import com.wheredidiputit.data.local.WdipiDatabase
import com.wheredidiputit.data.preferences.UserPreferences
import com.wheredidiputit.data.remote.SupabaseProvider
import com.wheredidiputit.data.remote.safeCall
import com.wheredidiputit.data.remote.toAppError
import com.wheredidiputit.data.sync.SyncScheduler
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.AppResult
import com.wheredidiputit.domain.repository.AccountRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
    private val database: WdipiDatabase,
    private val imageStore: ImageStore,
    private val preferences: UserPreferences,
    private val syncScheduler: SyncScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccountRepository {

    override suspend fun signOut() {
        syncScheduler.cancelAll()
        provider.client?.let { client ->
            // Tell the server when online; always clear the local session.
            safeCall { client.auth.signOut() }
            safeCall { client.auth.clearSession() }
        }
        wipeDevice()
    }

    override suspend fun deleteAccount(): AppResult<Unit> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        val response = safeCall { client.functions.invoke(AppConfig.DELETE_ACCOUNT_FUNCTION) }
            .getOrElse { return AppResult.Failure(it.toAppError()) }
        if (!response.status.isSuccess()) return AppResult.Failure(AppError.UNKNOWN)

        syncScheduler.cancelAll()
        safeCall { client.auth.clearSession() }
        wipeDevice()
        return AppResult.Success(Unit)
    }

    private suspend fun wipeDevice() = withContext(ioDispatcher) {
        database.clearAllTables()
        imageStore.clearAll()
        preferences.clearAccountData()
    }
}
