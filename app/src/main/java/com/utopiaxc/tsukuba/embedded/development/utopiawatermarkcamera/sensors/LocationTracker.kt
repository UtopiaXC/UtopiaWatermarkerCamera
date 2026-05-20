package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class LocationData(
    val location: Location? = null,
    val addressName: String? = null
)

class LocationTracker(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val geocoder = Geocoder(context, Locale.getDefault())

    private val _locationData = MutableStateFlow(LocationData())
    val locationData: StateFlow<LocationData> = _locationData.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            updateLocationAndGeocode(location)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        
        // Fetch last known quickly
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) updateLocationAndGeocode(loc)
        }
    }

    fun stop() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateLocationAndGeocode(location: Location) {
        val currentData = _locationData.value
        _locationData.value = currentData.copy(location = location)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        handleGeocodeResult(addresses)
                    }
                    override fun onError(errorMessage: String?) {}
                })
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (addresses != null) {
                    handleGeocodeResult(addresses)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Ignore network errors or unavailable grpc services
            // Keeps addressName as null or previous value
        }
    }

    private fun handleGeocodeResult(addresses: List<Address>) {
        if (addresses.isNotEmpty()) {
            val address = addresses[0]
            val nameParts = listOfNotNull(
                address.adminArea ?: address.subAdminArea,
                address.locality ?: address.subLocality,
                address.thoroughfare ?: address.subThoroughfare ?: address.featureName
            )
            val name = nameParts.joinToString(" ")
            if (name.isNotBlank()) {
                _locationData.value = _locationData.value.copy(addressName = name)
            }
        }
    }
}