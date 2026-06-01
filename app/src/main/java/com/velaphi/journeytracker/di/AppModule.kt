package com.velaphi.journeytracker.di

import com.velaphi.journeytracker.BuildConfig
import com.velaphi.journeytracker.core.network.TflAppKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @TflAppKey
    fun provideTflAppKey(): String = BuildConfig.TFL_APP_KEY
}
