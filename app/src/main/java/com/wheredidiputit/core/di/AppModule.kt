package com.wheredidiputit.core.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.wheredidiputit.data.local.CategoryDao
import com.wheredidiputit.data.local.ItemDao
import com.wheredidiputit.data.local.WdipiDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WdipiDatabase =
        Room.databaseBuilder(context, WdipiDatabase::class.java, WdipiDatabase.NAME).build()

    @Provides
    fun provideItemDao(database: WdipiDatabase): ItemDao = database.itemDao()

    @Provides
    fun provideCategoryDao(database: WdipiDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
