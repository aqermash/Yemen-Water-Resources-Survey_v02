package com.yemen.watersurvey.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yemen.watersurvey.core.location.GpsCaptureEvent
import com.yemen.watersurvey.core.location.GpsCaptureManager
import com.yemen.watersurvey.core.location.GpsCaptureState
import com.yemen.watersurvey.domain.model.GpsLocationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GpsCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val gpsManager = GpsCaptureManager(application)

    private val _state = MutableStateFlow(GpsCaptureState())
    val state: StateFlow<GpsCaptureState> = _state.asStateFlow()

    private var captureJob: Job? = null
    private var lastLocation: GpsLocationResult? = null
    private var updateCount = 0

    init {
        checkPermission()
    }

    fun onEvent(event: GpsCaptureEvent) {
        when (event) {
            GpsCaptureEvent.RequestPermission -> {
                // Permission request is handled by the UI via rememberLauncherForPermissionResult
                // This just updates the state tracking
            }
            GpsCaptureEvent.StartCapture -> startCapture()
            GpsCaptureEvent.StopCapture -> stopCapture()
            GpsCaptureEvent.RetryCapture -> startCapture()
        }
    }

    fun checkPermission() {
        val hasPermission = gpsManager.hasLocationPermission()
        _state.update { it.copy(hasPermission = hasPermission, permissionDenied = !hasPermission) }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted, permissionDenied = !granted) }
        if (granted) {
            startCapture()
        }
    }

    private fun startCapture() {
        if (!_state.value.hasPermission) {
            _state.update { it.copy(permissionDenied = true) }
            return
        }

        captureJob?.cancel()
        _state.update {
            it.copy(
                isCapturing = true,
                statusMessage = "جاري التقاط إحداثيات GPS...",
                currentLocation = null
            )
        }

        captureJob = viewModelScope.launch {
            updateCount = 0
            lastLocation = null

            gpsManager.locationUpdatesFlow()
                .catch { e ->
                    Log.e(TAG, "GPS capture error", e)
                    _state.update {
                        it.copy(
                            isCapturing = false,
                            statusMessage = "خطأ في التقاط GPS: ${e.message}"
                        )
                    }
                }
                .collect { location ->
                    updateCount++
                    val isImproving = lastLocation != null &&
                            location.accuracyM < (lastLocation?.accuracyM ?: Float.MAX_VALUE)
                    lastLocation = location

                    val accuracyEnough = location.accuracyM < GpsCaptureState.ACCURACY_THRESHOLD_METERS
                    val message = if (accuracyEnough) {
                        "GPS جاهز — دقة: %.1f متر".format(location.accuracyM)
                    } else {
                        "جاري تحسين الدقة: %.1f متر (أقل من %.0f متر مطلوب)".format(
                            location.accuracyM,
                            GpsCaptureState.ACCURACY_THRESHOLD_METERS
                        )
                    }

                    _state.update {
                        it.copy(
                            currentLocation = location,
                            isCapturing = true,
                            hasPermission = true,
                            permissionDenied = false,
                            accuracyEnoughToProceed = accuracyEnough,
                            statusMessage = message,
                            isAccuracyImproving = isImproving,
                            lastUpdateCount = updateCount
                        )
                    }

                    Log.d(TAG, "GPS update #$updateCount: lat=${location.latitude}, lon=${location.longitude}, " +
                            "accuracy=${location.accuracyM}m, quality=${location.quality}, " +
                            "enoughToProceed=$accuracyEnough")
                }
        }
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        _state.update {
            it.copy(
                isCapturing = false,
                statusMessage = "تم إيقاف التقاط GPS"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        captureJob?.cancel()
    }

    companion object {
        private const val TAG = "GpsCaptureViewModel"
    }
}