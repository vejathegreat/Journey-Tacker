package com.velaphi.journeytracker.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val query: String? = null,
    val total: Int? = null,
    val matches: List<SearchMatchDto>? = null,
)

@Serializable
data class SearchMatchDto(
    val id: String? = null,
    val name: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val towards: String? = null,
)

@Serializable
data class JourneyResultsResponseDto(
    val journeys: List<JourneyDto>? = null,
    @SerialName("fromLocationDisambiguation")
    val fromLocationDisambiguation: LocationDisambiguationDto? = null,
    @SerialName("toLocationDisambiguation")
    val toLocationDisambiguation: LocationDisambiguationDto? = null,
)

@Serializable
data class LocationDisambiguationDto(
    val disambiguationOptions: List<DisambiguationOptionDto>? = null,
)

@Serializable
data class DisambiguationOptionDto(
    val parameterValue: String? = null,
    val uri: String? = null,
    val place: PlaceDto? = null,
)

@Serializable
data class PlaceDto(
    val commonName: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
data class JourneyDto(
    val duration: Int? = null,
    val startDateTime: String? = null,
    val arrivalDateTime: String? = null,
    val legs: List<JourneyLegDto>? = null,
)

@Serializable
data class JourneyLegDto(
    val duration: Int? = null,
    val mode: IdentifierDto? = null,
    val instruction: InstructionDto? = null,
    val routeOptions: List<RouteOptionDto>? = null,
)

@Serializable
data class IdentifierDto(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class InstructionDto(
    val summary: String? = null,
    val detailed: String? = null,
)

@Serializable
data class RouteOptionDto(
    val id: String? = null,
    val name: String? = null,
    val lineIdentifier: IdentifierDto? = null,
)

@Serializable
data class PredictionDto(
    val vehicleId: String? = null,
    val naptanId: String? = null,
    val stationName: String? = null,
    val lineId: String? = null,
    val lineName: String? = null,
    val timeToStation: Int? = null,
    val towards: String? = null,
    val destinationName: String? = null,
)

@Serializable
data class RouteSequenceResponseDto(
    val lineId: String? = null,
    val lineName: String? = null,
    val direction: String? = null,
    val stations: List<MatchedStopDto>? = null,
    val stopPointSequences: List<StopPointSequenceDto>? = null,
)

@Serializable
data class StopPointSequenceDto(
    val stopPoint: List<MatchedStopDto>? = null,
)

@Serializable
data class MatchedStopDto(
    val id: String? = null,
    val name: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)
