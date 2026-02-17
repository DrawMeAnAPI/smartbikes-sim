package com.pjmbusnel.smartbikes.model

/**
 * Defines vehicle categories and their average cruising speeds.
 */
enum class VehicleType(val baseSpeedKph: Double) {
    E_MOTORBIKE(60.0),
    MOTORBIKE(75.0)
}