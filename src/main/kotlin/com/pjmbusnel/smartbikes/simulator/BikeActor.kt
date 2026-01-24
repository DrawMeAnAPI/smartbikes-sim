package com.pjmbusnel.smartbikes.simulator

import com.pjmbusnel.smartbikes.model.Coordinate
import com.pjmbusnel.smartbikes.model.Route
import com.pjmbusnel.smartbikes.utils.PhysicsUtils
import kotlinx.coroutines.*
import java.time.Instant

class BikeActor(
    val bikeId: String,
    private val route: Route,
    private val speedKph: Double = 25.0,
    private val tickRateMs: Long = 5000
) {
    private var currentPosition = route.points.first()
    private var pointIndex = 0
    private var isActive = true

    /**
     * The main simulation loop for this specific bike.
     */
    suspend fun startSimulation() = coroutineScope {
        println("[$bikeId] Starting delivery: ${route.name}")

        while (isActive && pointIndex < route.points.size - 1) {
            val start = currentPosition
            val destination = route.points[pointIndex + 1]

            // Calculate movement for this "tick"
            currentPosition = moveTowards(start, destination)

            // Log Telemetry (This is what we will eventually send to AWS IoT)
            logTelemetry()

            // Check if we reached the waypoint
            if (currentPosition == destination) {
                pointIndex++
            }

            delay(tickRateMs)
        }

        println("[$bikeId] Destination reached or simulation stopped.")
    }

    private fun moveTowards(start: Coordinate, end: Coordinate): Coordinate {
        val distanceToEnd = PhysicsUtils.calculateDistance(start, end)
        val metersPerTick = (speedKph * 1000 / 3600) * (tickRateMs / 1000.0)

        return if (distanceToEnd <= metersPerTick) {
            end // We arrive at the next waypoint
        } else {
            val fraction = metersPerTick / distanceToEnd
            PhysicsUtils.interpolate(start, end, fraction)
        }
    }

    private fun logTelemetry() {
        val timestamp = Instant.now()
        println("[$bikeId] | $timestamp | Pos: (${"%.5f".format(currentPosition.lat)}, ${"%.5f".format(currentPosition.lng)}) | State: MOVING")
    }

    fun stop() {
        isActive = false
    }
}