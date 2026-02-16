package com.pjmbusnel.smartbikes.model

/**
 * Defines vehicle categories and their average cruising speeds.
 */
enum class VehicleType(val baseSpeedKph: Double) {
    BIKE(35.0),
    MOTORBIKE(250.0)
}