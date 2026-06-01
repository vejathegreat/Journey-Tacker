package com.velaphi.journeytracker.core.data.mapper

import com.velaphi.journeytracker.core.model.DisambiguationOption
import com.velaphi.journeytracker.core.model.DisambiguationTarget
import com.velaphi.journeytracker.core.model.JourneyLeg
import com.velaphi.journeytracker.core.model.JourneyPlanResult
import com.velaphi.journeytracker.core.model.Location
import com.velaphi.journeytracker.core.model.PlannedJourney
import com.velaphi.journeytracker.core.model.RouteStop
import com.velaphi.journeytracker.core.model.VehiclePosition
import com.velaphi.journeytracker.core.network.dto.DisambiguationOptionDto
import com.velaphi.journeytracker.core.network.dto.JourneyDto
import com.velaphi.journeytracker.core.network.dto.JourneyLegDto
import com.velaphi.journeytracker.core.network.dto.JourneyResultsResponseDto
import com.velaphi.journeytracker.core.network.dto.MatchedStopDto
import com.velaphi.journeytracker.core.network.dto.PredictionDto
import com.velaphi.journeytracker.core.network.dto.RouteSequenceResponseDto
import com.velaphi.journeytracker.core.network.dto.SearchMatchDto

internal fun SearchMatchDto.toLocation(): Location? {
    val locationId = id ?: return null
    val locationName = name ?: return null
    val displayName = towards
        ?.takeIf { it.isNotBlank() }
        ?.let { "$locationName · $it" }
        ?: locationName
    return Location(
        id = locationId,
        name = displayName,
        latitude = lat,
        longitude = lon,
    )
}

internal fun JourneyResultsResponseDto.toJourneyPlanResult(): JourneyPlanResult {
    fromLocationDisambiguation
        ?.disambiguationOptions
        ?.takeIf { it.isNotEmpty() }
        ?.let { options ->
            return JourneyPlanResult.DisambiguationRequired(
                target = DisambiguationTarget.ORIGIN,
                options = options.mapNotNull { it.toDisambiguationOption() },
            )
        }

    toLocationDisambiguation
        ?.disambiguationOptions
        ?.takeIf { it.isNotEmpty() }
        ?.let { options ->
            return JourneyPlanResult.DisambiguationRequired(
                target = DisambiguationTarget.DESTINATION,
                options = options.mapNotNull { it.toDisambiguationOption() },
            )
        }

    val plannedJourneys = journeys.orEmpty().mapNotNull { it.toPlannedJourney() }
    return JourneyPlanResult.Success(plannedJourneys)
}

private fun DisambiguationOptionDto.toDisambiguationOption(): DisambiguationOption? {
    val value = parameterValue ?: return null
    val name = place?.commonName ?: value
    return DisambiguationOption(parameterValue = value, displayName = name)
}

private fun JourneyDto.toPlannedJourney(): PlannedJourney? {
    val busLegs = legs.orEmpty().mapNotNull { it.toBusLeg() }
    if (busLegs.isEmpty()) return null
    return PlannedJourney(
        durationMinutes = (duration ?: 0) / 60,
        legs = busLegs,
    )
}

private fun JourneyLegDto.toBusLeg(): JourneyLeg? {
    val modeName = mode?.name?.lowercase() ?: return null
    if (modeName != "bus") return null

    val route = routeOptions?.firstOrNull() ?: return null
    val lineId = route.lineIdentifier?.id ?: route.name ?: return null
    val lineName = route.name ?: lineId

    return JourneyLeg(
        mode = modeName,
        lineId = lineId,
        lineName = lineName,
        destination = route.lineIdentifier?.name.orEmpty(),
        instruction = instruction?.summary ?: instruction?.detailed.orEmpty(),
        durationMinutes = (duration ?: 0) / 60,
    )
}

internal fun RouteSequenceResponseDto.toRouteStops(): List<RouteStop> {
    val sequenceStops = stopPointSequences
        ?.flatMap { it.stopPoint.orEmpty() }
        .orEmpty()

    val source = sequenceStops.ifEmpty { stations.orEmpty() }

    return source.mapNotNull { it.toRouteStop() }
}

private fun MatchedStopDto.toRouteStop(): RouteStop? {
    val stopId = id ?: return null
    val stopName = name ?: return null
    val latitude = lat ?: return null
    val longitude = lon ?: return null
    return RouteStop(
        id = stopId,
        name = stopName,
        latitude = latitude,
        longitude = longitude,
    )
}

internal fun List<PredictionDto>.toVehiclePositions(
    lineId: String,
    routeStops: List<RouteStop>,
): List<VehiclePosition> {
    val stopsById = routeStops.associateBy { it.id }

    return this
        .filter { prediction ->
            !prediction.vehicleId.isNullOrBlank() && !prediction.naptanId.isNullOrBlank()
        }
        .distinctBy { it.vehicleId }
        .mapNotNull { prediction ->
            val vehicleId = prediction.vehicleId ?: return@mapNotNull null
            val stopId = prediction.naptanId ?: return@mapNotNull null
            val stop = stopsById[stopId] ?: return@mapNotNull null

            VehiclePosition(
                vehicleId = vehicleId,
                lineId = prediction.lineId ?: lineId,
                stopId = stopId,
                stopName = prediction.stationName ?: stop.name,
                latitude = stop.latitude,
                longitude = stop.longitude,
                timeToStationSeconds = prediction.timeToStation ?: 0,
                towards = prediction.towards ?: prediction.destinationName.orEmpty(),
            )
        }
}
