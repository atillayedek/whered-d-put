package com.wheredidiputit.data.repository

import com.wheredidiputit.data.preferences.UserPreferences
import com.wheredidiputit.domain.model.ThemeMode
import com.wheredidiputit.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val preferences: UserPreferences,
) : SettingsRepository {
    override val onboardingCompleted: Flow<Boolean> = preferences.onboardingCompleted
    override val themeMode: Flow<ThemeMode> = preferences.themeMode

    override suspend fun setOnboardingCompleted() = preferences.setOnboardingCompleted()

    override suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)
}
