package com.velaphi.journeytracker.core.model

import kotlinx.coroutines.flow.Flow

interface JourneyRepository {
    suspend fun searchLocations(query: String): Result<List<Location>>

    suspend fun planJourney(from: String, to: String): Result<JourneyPlanResult>
}

interface TrackingRepository {
    fun observeTracking(lineId: String, pollingIntervalMs: Long = 30_000L): Flow<Result<TrackingSnapshot>>
}
