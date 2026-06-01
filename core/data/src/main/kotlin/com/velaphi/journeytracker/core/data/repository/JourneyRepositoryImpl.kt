package com.velaphi.journeytracker.core.data.repository

import com.velaphi.journeytracker.core.common.DispatcherProvider
import com.velaphi.journeytracker.core.data.mapper.toJourneyPlanResult
import com.velaphi.journeytracker.core.data.mapper.toLocation
import com.velaphi.journeytracker.core.data.mapper.toRouteStops
import com.velaphi.journeytracker.core.data.mapper.toVehiclePositions
import com.velaphi.journeytracker.core.model.JourneyPlanResult
import com.velaphi.journeytracker.core.model.JourneyRepository
import com.velaphi.journeytracker.core.model.Location
import com.velaphi.journeytracker.core.model.TrackingRepository
import com.velaphi.journeytracker.core.model.TrackingSnapshot
import com.velaphi.journeytracker.core.network.TflAppKey
import com.velaphi.journeytracker.core.network.api.TflApi
import com.velaphi.journeytracker.core.network.mapTflErrors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

class JourneyRepositoryImpl @Inject constructor(
    private val tflApi: TflApi,
    @TflAppKey private val appKey: String,
    private val dispatchers: DispatcherProvider,
) : JourneyRepository {

    override suspend fun searchLocations(query: String): Result<List<Location>> = withContext(dispatchers.io) {
        runCatching {
            if (appKey.isBlank()) error("Add TFL_APP_KEY to local.properties")
            if (query.isBlank()) return@runCatching emptyList()

            tflApi.searchStopPoints(query = query, appKey = appKey)
                .matches
                .orEmpty()
                .mapNotNull { it.toLocation() }
        }.mapTflErrors()
    }

    override suspend fun planJourney(from: String, to: String): Result<JourneyPlanResult> =
        withContext(dispatchers.io) {
            runCatching {
                if (appKey.isBlank()) error("Add TFL_APP_KEY to local.properties")
                if (from.isBlank() || to.isBlank()) error("Origin and destination are required")

                tflApi.getJourneyResults(from = from, to = to, appKey = appKey)
                    .toJourneyPlanResult()
            }.mapTflErrors()
        }
}

class TrackingRepositoryImpl @Inject constructor(
    private val tflApi: TflApi,
    @TflAppKey private val appKey: String,
    private val dispatchers: DispatcherProvider,
) : TrackingRepository {

    override fun observeTracking(
        lineId: String,
        pollingIntervalMs: Long,
    ): Flow<Result<TrackingSnapshot>> = flow {
        if (appKey.isBlank()) {
            emit(Result.failure(IllegalStateException("Add TFL_APP_KEY to local.properties")))
            return@flow
        }

        var cachedRouteStops = emptyList<com.velaphi.journeytracker.core.model.RouteStop>()

        while (currentCoroutineContext().isActive) {
            emit(
                runCatching {
                    withContext(dispatchers.io) {
                        if (cachedRouteStops.isEmpty()) {
                            cachedRouteStops = loadRouteGeometry(lineId)
                        }

                        val arrivals = tflApi.getLineArrivals(lineId = lineId, appKey = appKey)
                        val vehicles = arrivals.toVehiclePositions(
                            lineId = lineId,
                            routeStops = cachedRouteStops,
                        )

                        TrackingSnapshot(
                            routeStops = cachedRouteStops,
                            vehicles = vehicles,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                }.mapTflErrors(),
            )
            delay(pollingIntervalMs)
        }
    }

    private suspend fun loadRouteGeometry(lineId: String): List<com.velaphi.journeytracker.core.model.RouteStop> {
        val outbound = tflApi.getRouteSequence(
            lineId = lineId,
            direction = "outbound",
            appKey = appKey,
        ).toRouteStops()
        val inbound = runCatching {
            tflApi.getRouteSequence(
                lineId = lineId,
                direction = "inbound",
                appKey = appKey,
            ).toRouteStops()
        }.getOrDefault(emptyList())

        return (outbound + inbound).distinctBy { it.id }
    }
}
