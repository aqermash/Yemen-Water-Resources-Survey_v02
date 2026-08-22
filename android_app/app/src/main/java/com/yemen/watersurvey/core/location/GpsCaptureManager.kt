package com.yemen.watersurvey.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yemen.watersurvey.domain.model.GpsAccuracyQuality
import com.yemen.watersurvey.domain.model.GpsLocationResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GpsCaptureManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun locationUpdatesFlow(): Flow<GpsLocationResult> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateDelayMillis(5000L)
            .setMinUpdateDistanceMeters(0.5f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val gpsResult = GpsLocationResult(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitudeM = if (location.hasAltitude()) location.altitude else null,
                        accuracyM = location.accuracy,
                        quality = GpsAccuracyQuality.classify(location.accuracy),
                        capturedAt = dateFormat.format(Date()),
                        provider = location.provider ?: "GPS_WGS84"
                    )
                    trySend(gpsResult)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            Log.d(TAG, "Stopping GPS location updates")
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(callback: (GpsLocationResult?) -> Unit) {
        if (!hasLocationPermission()) {
            callback(null)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val gpsResult = GpsLocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeM = if (location.hasAltitude()) location.altitude else null,
                    accuracyM = location.accuracy,
                    quality = GpsAccuracyQuality.classify(location.accuracy),
                    capturedAt = dateFormat.format(Date()),
                    provider = location.provider ?: "GPS_WGS84"
                )
                callback(gpsResult)
            } else {
                callback(null)
            }
        }.addOnFailureListener {
            Log.e(TAG, "Failed to get last known location", it)
            callback(null)
        }
    }

    companion object {
        private const val TAG = "GpsCaptureManager"
    }
}