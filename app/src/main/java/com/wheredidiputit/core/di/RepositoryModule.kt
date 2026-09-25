package com.wheredidiputit.core.di

import com.wheredidiputit.data.image.PhotoRepositoryImpl
import com.wheredidiputit.data.repository.AccountRepositoryImpl
import com.wheredidiputit.data.repository.AuthRepositoryImpl
import com.wheredidiputit.data.repository.CategoryRepositoryImpl
import com.wheredidiputit.data.repository.ItemRepositoryImpl
import com.wheredidiputit.data.repository.SettingsRepositoryImpl
import com.wheredidiputit.data.sync.SyncScheduler
import com.wheredidiputit.domain.repository.AccountRepository
import com.wheredidiputit.domain.repository.AuthRepository
import com.wheredidiputit.domain.repository.CategoryRepository
import com.wheredidiputit.domain.repository.ItemRepository
import com.wheredidiputit.domain.repository.PhotoRepository
import com.wheredidiputit.domain.repository.SettingsRepository
import com.wheredidiputit.domain.repository.SyncController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun bindItemRepository(impl: ItemRepositoryImpl): ItemRepository
    @Binds abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository
    @Binds abstract fun bindPhotoRepository(impl: PhotoRepositoryImpl): PhotoRepository
    @Binds abstract fun bindSyncController(impl: SyncScheduler): SyncController
}
