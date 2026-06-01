package com.velaphi.journeytracker.feature.tracking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velaphi.journeytracker.core.model.RouteStop
import com.velaphi.journeytracker.core.model.TrackingRepository
import com.velaphi.journeytracker.core.model.VehiclePosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface TrackingUiState {
    data object Loading : TrackingUiState

    data class Success(
        val lineId: String,
        val vehicles: List<VehiclePosition>,
        val routeStops: List<RouteStop>,
        val lastUpdatedLabel: String,
    ) : TrackingUiState

    data class Error(val message: String) : TrackingUiState
}

@HiltViewModel
class TrackingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    trackingRepository: TrackingRepository,
) : ViewModel() {
    val lineId: String = checkNotNull(savedStateHandle["lineId"])

    val uiState = trackingRepository.observeTracking(lineId)
        .map { result ->
            result.fold(
                onSuccess = { snapshot ->
                    val busCount = snapshot.vehicles.size
                    TrackingUiState.Success(
                        lineId = lineId,
                        vehicles = snapshot.vehicles,
                        routeStops = snapshot.routeStops,
                        lastUpdatedLabel = buildString {
                            append("$busCount active bus")
                            if (busCount != 1) append("es")
                            append(" · updated just now")
                            append(" · refreshes every 30s")
                        },
                    )
                },
                onFailure = { error ->
                    TrackingUiState.Error(error.message ?: "Unable to load live buses")
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TrackingUiState.Loading,
        )
}
