package com.velaphi.journeytracker.feature.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.velaphi.journeytracker.core.model.RouteStop
import com.velaphi.journeytracker.core.model.VehiclePosition
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Route ${viewModel.lineId}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            TrackingUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is TrackingUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is TrackingUiState.Success -> {
                TrackingContent(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun TrackingContent(
    state: TrackingUiState.Success,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = state.lastUpdatedLabel,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        VehicleMap(
            routeStops = state.routeStops,
            vehicles = state.vehicles,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (state.vehicles.isEmpty()) {
                item {
                    Text(
                        text = "No active buses on this route right now",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.vehicles, key = { it.vehicleId }) { vehicle ->
                    VehicleCard(vehicle = vehicle)
                }
            }
        }
    }
}

@Composable
private fun VehicleMap(
    routeStops: List<RouteStop>,
    vehicles: List<VehiclePosition>,
    modifier: Modifier = Modifier,
) {
    val defaultLondon = LatLng(51.5074, -0.1278)
    val routePoints = remember(routeStops) {
        routeStops.map { LatLng(it.latitude, it.longitude) }
    }
    val cameraTarget = remember(vehicles, routePoints) {
        vehicles.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
            ?: routePoints.firstOrNull()
            ?: defaultLondon
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(cameraTarget, 13f)
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(zoomControlsEnabled = true),
    ) {
        if (routePoints.size >= 2) {
            Polyline(
                points = routePoints,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                width = 10f,
            )
        }

        routeStops.forEach { stop ->
            Marker(
                state = MarkerState(LatLng(stop.latitude, stop.longitude)),
                title = stop.name,
                snippet = "Route stop",
                alpha = 0.45f,
            )
        }

        vehicles.forEach { vehicle ->
            Marker(
                state = MarkerState(LatLng(vehicle.latitude, vehicle.longitude)),
                title = "Bus ${vehicle.lineId}",
                snippet = proximityLabel(vehicle.timeToStationSeconds),
            )
        }
    }
}

private fun proximityLabel(timeToStationSeconds: Int): String {
    val minutes = timeToStationSeconds / 60
    val seconds = timeToStationSeconds % 60
    return when {
        timeToStationSeconds <= 0 -> "At stop"
        minutes > 0 -> "Approaching in ${minutes}m ${seconds}s"
        else -> "Approaching in ${seconds}s"
    }
}

@Composable
private fun VehicleCard(vehicle: VehiclePosition) {
    val progress = remember(vehicle.timeToStationSeconds) {
        val capped = min(vehicle.timeToStationSeconds, 600)
        1f - (capped / 600f)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Vehicle ${vehicle.vehicleId}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Approaching ${vehicle.stopName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = proximityLabel(vehicle.timeToStationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = vehicle.towards,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
