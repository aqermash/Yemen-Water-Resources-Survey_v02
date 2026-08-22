package com.yemen.watersurvey.core.location

import com.yemen.watersurvey.domain.model.GpsAccuracyQuality
import com.yemen.watersurvey.domain.model.GpsLocationResult

data class GpsCaptureState(
    val currentLocation: GpsLocationResult? = null,
    val isCapturing: Boolean = false,
    val hasPermission: Boolean = false,
    val permissionDenied: Boolean = false,
    val accuracyEnoughToProceed: Boolean = false,
    val statusMessage: String = "",
    val isAccuracyImproving: Boolean = false,
    val lastUpdateCount: Int = 0
) {
    companion object {
        const val ACCURACY_THRESHOLD_METERS = 15.0f
    }
}

sealed class GpsCaptureEvent {
    object RequestPermission : GpsCaptureEvent()
    object StartCapture : GpsCaptureEvent()
    object StopCapture : GpsCaptureEvent()
    object RetryCapture : GpsCaptureEvent()
}