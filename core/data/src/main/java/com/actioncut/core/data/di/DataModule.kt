package com.actioncut.core.data.di

import android.content.Context
import androidx.room.Room
import com.actioncut.core.common.coroutine.DispatcherProvider
import com.actioncut.core.common.coroutine.StandardDispatcherProvider
import com.actioncut.core.data.local.ActionCutDatabase
import com.actioncut.core.data.local.dao.ProjectDao
import com.actioncut.core.data.repository.MediaRepositoryImpl
import com.actioncut.core.data.repository.ProjectRepositoryImpl
import com.actioncut.core.domain.repository.MediaRepository
import com.actioncut.core.domain.repository.ProjectRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/** Provides concrete data-layer singletons (DB, DAO, JSON). */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ActionCutDatabase =
        Room.databaseBuilder(context, ActionCutDatabase::class.java, ActionCutDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProjectDao(database: ActionCutDatabase): ProjectDao = database.projectDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
}

/** Binds repository/dispatcher interfaces to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    abstract fun bindDispatcherProvider(impl: StandardDispatcherProvider): DispatcherProvider
}
