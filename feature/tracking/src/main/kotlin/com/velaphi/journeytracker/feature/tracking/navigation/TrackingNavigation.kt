package com.velaphi.journeytracker.feature.tracking.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.velaphi.journeytracker.feature.tracking.TrackingScreen

object TrackingRoute {
    const val ROUTE = "tracking/{lineId}"
    const val LINE_ID_ARG = "lineId"

    fun create(lineId: String): String = "tracking/$lineId"
}

fun NavGraphBuilder.trackingScreen(
    onNavigateBack: () -> Unit,
) {
    composable(
        route = TrackingRoute.ROUTE,
        arguments = listOf(
            navArgument(TrackingRoute.LINE_ID_ARG) { type = NavType.StringType },
        ),
    ) {
        TrackingScreen(onNavigateBack = onNavigateBack)
    }
}
