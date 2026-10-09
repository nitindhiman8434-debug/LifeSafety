package com.lifesafety.driversafety.admin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

/** One driver's last known position on a map. */
data class MapPin(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String?,
    /** Red marker: over the limit right now. */
    val alert: Boolean
)

/**
 * A Google Map with one marker per pin. The map does not pan by finger (it sits inside a scrolling page),
 * it follows the drivers instead: one pin is centred, several are fitted into view.
 * Needs MAPS_API_KEY in local.properties; without it the map stays blank (docs/phase-3-setup.md).
 */
@Composable
fun DriverMap(pins: List<MapPin>, modifier: Modifier = Modifier) {
    val cameraPositionState = rememberCameraPositionState()
    var mapLoaded by remember { mutableStateOf(false) }
    val positionsKey = pins.joinToString { "${it.id}:${it.latitude},${it.longitude}" }

    LaunchedEffect(mapLoaded, positionsKey) {
        if (!mapLoaded || pins.isEmpty()) return@LaunchedEffect
        try {
            if (pins.size == 1) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(pins[0].latitude, pins[0].longitude), 15f), 700)
            } else {
                val bounds = LatLngBounds.builder().apply { pins.forEach { include(LatLng(it.latitude, it.longitude)) } }.build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120), 700)
            }
        } catch (e: Exception) {
            // The map had no size yet (a bounds move needs a laid-out map): fall back to the first pin.
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(pins[0].latitude, pins[0].longitude), 12f))
        }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                scrollGesturesEnabled = false,
                zoomControlsEnabled = true,
                zoomGesturesEnabled = true,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false
            ),
            onMapLoaded = { mapLoaded = true }
        ) {
            pins.forEach { pin ->
                val state = remember(pin.id) { MarkerState(LatLng(pin.latitude, pin.longitude)) }
                state.position = LatLng(pin.latitude, pin.longitude)
                Marker(
                    state = state,
                    title = pin.title,
                    snippet = pin.snippet,
                    icon = BitmapDescriptorFactory.defaultMarker(
                        if (pin.alert) BitmapDescriptorFactory.HUE_RED else BitmapDescriptorFactory.HUE_AZURE
                    )
                )
            }
        }
    }
}

/** Opens the position in the Google Maps app (or any maps app). Nothing happens if none is installed. */
fun openInMaps(context: Context, latitude: Double, longitude: Double, label: String) {
    val coords = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
    val uri = Uri.parse("geo:$coords?q=$coords(${Uri.encode(label)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        // No maps app on this phone.
    }
}

/** Opens the phone's dialer with the number filled in. No call permission needed; the admin taps Call. */
fun dial(context: Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
    } catch (e: ActivityNotFoundException) {
        // A tablet without a phone app.
    }
}
