package com.velaphi.journeytracker.core.model

data class Location(
    val id: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class DisambiguationOption(
    val parameterValue: String,
    val displayName: String,
)

enum class DisambiguationTarget {
    ORIGIN,
    DESTINATION,
}

sealed interface JourneyPlanResult {
    data class Success(val journeys: List<PlannedJourney>) : JourneyPlanResult

    data class DisambiguationRequired(
        val target: DisambiguationTarget,
        val options: List<DisambiguationOption>,
    ) : JourneyPlanResult
}

data class PlannedJourney(
    val durationMinutes: Int,
    val legs: List<JourneyLeg>,
)

data class JourneyLeg(
    val mode: String,
    val lineId: String,
    val lineName: String,
    val destination: String,
    val instruction: String,
    val durationMinutes: Int,
)

data class RouteStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class BusArrival(
    val vehicleId: String,
    val stopId: String,
    val stopName: String,
    val lineId: String,
    val timeToStationSeconds: Int,
    val towards: String,
)

data class VehiclePosition(
    val vehicleId: String,
    val lineId: String,
    val stopId: String,
    val stopName: String,
    val latitude: Double,
    val longitude: Double,
    val timeToStationSeconds: Int,
    val towards: String,
)

data class TrackingSnapshot(
    val routeStops: List<RouteStop>,
    val vehicles: List<VehiclePosition>,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
