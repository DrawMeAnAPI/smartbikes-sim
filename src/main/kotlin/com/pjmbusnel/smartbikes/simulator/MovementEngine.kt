package com.pjmbusnel.smartbikes.simulator

import com.pjmbusnel.smartbikes.model.Coordinate
import com.pjmbusnel.smartbikes.model.Route
import com.pjmbusnel.smartbikes.utils.PhysicsUtils
import kotlin.random.Random

class MovementEngine(
    private val route: Route,
    private val baseSpeedKph: Double
) {
    private var currentPointIndex = 0
    private var currentPosition = route.points.first()

    // We store the "actual" current speed for reporting
    var currentSpeedKph: Double = baseSpeedKph
        private set

    fun tick(secondsPassed: Int): Coordinate {
        if (isFinished()) return route.points.last()

        // 1. Simulate light speed variation (+/- 10% of base speed)
        val variance = baseSpeedKph * 0.1
        currentSpeedKph = baseSpeedKph + Random.nextDouble(-variance, variance)

        // 2. Convert current variable speed to meters per second
        val metersPerSecond = (currentSpeedKph * 1000.0) / 3600.0
        val nextWaypoint = route.points[currentPointIndex + 1]
        val distanceToNext = PhysicsUtils.calculateDistance(currentPosition, nextWaypoint)
        val distanceTraveled = metersPerSecond * secondsPassed

        return if (distanceTraveled >= distanceToNext) {
            currentPointIndex++
            currentPosition = nextWaypoint
            currentPosition
        } else {
            val fraction = distanceTraveled / distanceToNext
            currentPosition = PhysicsUtils.interpolate(currentPosition, nextWaypoint, fraction)
            currentPosition
        }
    }

    fun isFinished(): Boolean = currentPointIndex >= route.points.size - 1
}