package com.soll.domain.command.handlers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class LocationHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "location"
    override val description = "Get current GPS location"

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    override suspend fun execute(message: Message, args: String?) {
        if (!hasPermission()) {
            reply(message, "Location permission not granted. Please grant location permission in app settings.")
            return
        }

        if (!isLocationEnabled()) {
            reply(message, "Location services are disabled. Please enable GPS in device settings.")
            return
        }

        reply(message, "📍 Getting location...")

        try {
            val location = getCurrentLocation()

            if (location == null) {
                reply(message, "❌ Could not get location. Make sure GPS is enabled and try again.")
                return
            }

            val text = buildLocationMessage(location)
            reply(message, text)

            // Also send location as Telegram location message
            telegramRepository.sendLocation(
                chatId = message.chat.id,
                latitude = location.latitude,
                longitude = location.longitude
            )

        } catch (e: Exception) {
            Timber.e(e, "Error getting location")
            reply(message, "❌ Error getting location: ${e.message}")
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                continuation.resume(location)
            }.addOnFailureListener { e ->
                Timber.e(e, "Failed to get current location")
                continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }

    private fun buildLocationMessage(location: Location): String {
        return buildString {
            append("<b>📍 Current Location</b>\n\n")

            append("<b>Coordinates:</b>\n")
            append("Lat: ${String.format(Locale.US, "%.6f", location.latitude)}\n")
            append("Lon: ${String.format(Locale.US, "%.6f", location.longitude)}\n\n")

            if (location.hasAccuracy()) {
                append("<b>Accuracy:</b> ${location.accuracy.toInt()} m\n")
            }

            if (location.hasAltitude()) {
                append("<b>Altitude:</b> ${location.altitude.toInt()} m\n")
            }

            if (location.hasSpeed()) {
                val speedKmh = location.speed * 3.6
                append("<b>Speed:</b> ${String.format(Locale.US, "%.1f", speedKmh)} km/h\n")
            }

            if (location.hasBearing()) {
                append("<b>Bearing:</b> ${location.bearing.toInt()}°\n")
            }

            append("<b>Time:</b> ${dateFormat.format(Date(location.time))}\n\n")

            // Try to get address
            val address = getAddressFromLocation(location)
            if (address != null) {
                append("<b>Address:</b>\n$address\n\n")
            }

            // Google Maps link
            append("<b>Google Maps:</b>\n")
            append("https://maps.google.com/?q=${location.latitude},${location.longitude}")
        }
    }

    @Suppress("DEPRECATION")
    private fun getAddressFromLocation(location: Location): String? {
        return try {
            if (!Geocoder.isPresent()) return null

            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            addresses?.firstOrNull()?.let { address ->
                buildString {
                    for (i in 0..address.maxAddressLineIndex) {
                        if (i > 0) append("\n")
                        append(address.getAddressLine(i))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting address")
            null
        }
    }
}
