package com.pjmbusnel.smartbikes.utils

import com.pjmbusnel.smartbikes.model.Coordinate
import kotlin.math.*

object PhysicsUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the distance between two points using the Haversine formula.
     */
    fun calculateDistance(start: Coordinate, end: Coordinate): Double {
        val dLat = Math.toRadians(end.lat - start.lat)
        val dLon = Math.toRadians(end.lng - start.lng)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(start.lat)) * cos(Math.toRadians(end.lat)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Finds a point between [start] and [end] based on a [fraction] (0.0 to 1.0).
     */
    fun interpolate(start: Coordinate, end: Coordinate, fraction: Double): Coordinate {
        val lat = start.lat + (end.lat - start.lat) * fraction
        val lng = start.lng + (end.lng - start.lng) * fraction
        return Coordinate(lat, lng)
    }
}