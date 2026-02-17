package com.pjmbusnel.smartbikes.model

import java.time.Instant

data class Telemetry(
    val bikeId: String,
    val vehicleType: String,
    val lat: Double,
    val lng: Double,
    val speedKph: Double,
    val batteryLevel: Double? = null, // only for electric
    val timestamp: String = Instant.now().toString()
)