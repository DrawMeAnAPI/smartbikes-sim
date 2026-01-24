package com.pjmbusnel.smartbikes.model

/**
 * Defines vehicle categories and their average cruising speeds.
 */
enum class VehicleType(val baseSpeedKph: Double) {
    BIKE(15.0),
    MOTORBIKE(45.0)
}