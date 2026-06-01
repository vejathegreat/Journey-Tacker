package com.velaphi.journeytracker.core.data.di

import com.velaphi.journeytracker.core.common.DefaultDispatcherProvider
import com.velaphi.journeytracker.core.common.DispatcherProvider
import com.velaphi.journeytracker.core.data.repository.JourneyRepositoryImpl
import com.velaphi.journeytracker.core.data.repository.TrackingRepositoryImpl
import com.velaphi.journeytracker.core.model.JourneyRepository
import com.velaphi.journeytracker.core.model.TrackingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindJourneyRepository(
        impl: JourneyRepositoryImpl,
    ): JourneyRepository

    @Binds
    @Singleton
    abstract fun bindTrackingRepository(
        impl: TrackingRepositoryImpl,
    ): TrackingRepository

    companion object {
        @Provides
        @Singleton
        fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
    }
}
