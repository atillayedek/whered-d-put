package com.wheredidiputit.domain.repository

import com.wheredidiputit.domain.model.AppResult
import com.wheredidiputit.domain.model.AuthRedirectResult
import com.wheredidiputit.domain.model.AuthState
import com.wheredidiputit.domain.model.Category
import com.wheredidiputit.domain.model.Item
import com.wheredidiputit.domain.model.ItemDraft
import com.wheredidiputit.domain.model.SignUpResult
import com.wheredidiputit.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val isBackendConfigured: Boolean

    suspend fun signIn(email: String, password: String): AppResult<Unit>
    suspend fun signUp(email: String, password: String): AppResult<SignUpResult>
    suspend fun resendVerificationEmail(email: String): AppResult<Unit>
    suspend fun sendPasswordReset(email: String): AppResult<Unit>
    suspend fun updatePassword(newPassword: String): AppResult<Unit>
    suspend fun handleAuthRedirect(url: String): AuthRedirectResult
}

interface ItemRepository {
    fun observeRecent(): Flow<List<Item>>
    fun observeFavorites(): Flow<List<Item>>
    fun search(query: String): Flow<List<Item>>
    fun observeItem(id: String): Flow<Item?>
    fun observeFailedSyncCount(): Flow<Int>

    /** Memories that count towards the free plan's limit. */
    fun observeActiveCount(): Flow<Int>

    suspend fun create(draft: ItemDraft): AppResult<String>
    suspend fun update(id: String, draft: ItemDraft): AppResult<Unit>
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun delete(id: String)
    suspend fun unsyncedCount(): Int
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun createCategory(name: String): AppResult<Category>
}

interface SettingsRepository {
    val onboardingCompleted: Flow<Boolean>
    val themeMode: Flow<ThemeMode>

    suspend fun setOnboardingCompleted()
    suspend fun setThemeMode(mode: ThemeMode)
}

interface AccountRepository {
    /** Signs out and removes every memory, photo and cache from this device. */
    suspend fun signOut()

    /** Permanently deletes the account and all of its cloud and local data. */
    suspend fun deleteAccount(): AppResult<Unit>
}

interface SyncController {
    fun requestSync()
}
