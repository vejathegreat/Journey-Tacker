package com.velaphi.journeytracker.core.network.api

import com.velaphi.journeytracker.core.network.dto.JourneyResultsResponseDto
import com.velaphi.journeytracker.core.network.dto.PredictionDto
import com.velaphi.journeytracker.core.network.dto.RouteSequenceResponseDto
import com.velaphi.journeytracker.core.network.dto.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TflApi {

    @GET("StopPoint/Search/{query}")
    suspend fun searchStopPoints(
        @Path("query") query: String,
        @Query("modes") modes: String = "bus",
        @Query("app_key") appKey: String,
    ): SearchResponseDto

    @GET("Journey/JourneyResults/{from}/to/{to}")
    suspend fun getJourneyResults(
        @Path("from") from: String,
        @Path("to") to: String,
        @Query("mode") mode: String = "bus",
        @Query("app_key") appKey: String,
    ): JourneyResultsResponseDto

    @GET("Line/{lineId}/Arrivals")
    suspend fun getLineArrivals(
        @Path("lineId") lineId: String,
        @Query("app_key") appKey: String,
    ): List<PredictionDto>

    @GET("Line/{lineId}/Route/Sequence/{direction}")
    suspend fun getRouteSequence(
        @Path("lineId") lineId: String,
        @Path("direction") direction: String,
        @Query("app_key") appKey: String,
    ): RouteSequenceResponseDto
}
