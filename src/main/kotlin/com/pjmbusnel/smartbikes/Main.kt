package com.pjmbusnel.smartbikes

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pjmbusnel.smartbikes.io.ItineraryLoader
import com.pjmbusnel.smartbikes.model.BikeTripConfig
import com.pjmbusnel.smartbikes.model.Telemetry
import com.pjmbusnel.smartbikes.model.VehicleType
import com.pjmbusnel.smartbikes.simulator.MovementEngine
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.Collections.synchronizedSet
import kotlin.time.Clock
import com.pjmbusnel.smartbikes.model.Route as BikeRoute

// Global state for WebSocket sessions
private val jsonMapper = jacksonObjectMapper()
private val sessions = synchronizedSet(mutableSetOf<WebSocketServerSession>())
private val tickSeconds = 1

fun main() {
    val server = embeddedServer(factory = Netty, port = 8080) {
        install(WebSockets)
        routing {
            webSocket("/fleet") {
                sessions.add(this)
                try {
                    for (frame in incoming) { /* Keep alive */ }
                } catch (e: Exception) {
                    println("Connection closed: ${e.message}")
                } finally {
                    sessions.remove(this)
                    broadcastSessionCount() // Broadcast update on leave
                }
            }
        }
    }

    // Launch simulation coroutines in the background
    CoroutineScope(Dispatchers.Default).launch {
        val loader = ItineraryLoader()
        val library = loader.loadTrips()
        val fleet = loader.loadFleetConfig()

        println("--- Starting Fleet Simulation ---")

        fleet.forEach { config ->
            val tripDef = library.trips.find { it.id == config.tripId }

            if (tripDef != null) {
                launch {
                    // Assemble route with proper point-by-point reversal
                    val finalRoute = loader.assembleRoute(tripDef, library.segments, tripReverse = config.reverse)
                    val vehicleId = if (config.reverse) "${config.id}-R" else config.id
                    val vehicleType = VehicleType.valueOf(config.type.uppercase())

                    println("Launching $vehicleId on ${finalRoute.name}")

                    runVehicle(
                        id = vehicleId,
                        initialRoute = finalRoute,
                        initialType = vehicleType,
                        scope = this,
                        config = config
                    )
                }
            } else {
                println("Warning: Trip '${config.tripId}' not found for vehicle ${config.id}")
            }
        }
    }

    println("Server starting...")
    server.start(wait = true)
}

private suspend fun runVehicle(
    id: String,
    initialRoute: BikeRoute,
    initialType: VehicleType,
    scope: CoroutineScope,
    config: BikeTripConfig
) {
    var currentRoute = initialRoute
    var currentType = initialType

    while (true) {
        val engine = MovementEngine(currentRoute, currentType.baseSpeedKph)
        println("Vehicle $id starting leg as ${currentType.name} on route ${currentRoute.id}")

        while (!engine.isFinished()) {
            val newPos = engine.tick(tickSeconds)

            val telemetry = Telemetry(
                bikeId = id,
                vehicleType = currentType.name,
                lat = newPos.lat,
                lng = newPos.lng,
                speedKph = engine.currentSpeedKph,
                timestamp = Clock.System.now().toString()
            )

            val json = jsonMapper.writeValueAsString(telemetry)

            synchronized(sessions) {
                sessions.toList().forEach { session ->
                    scope.launch {
                        try {
                            session.send(json)
                        } catch (e: Exception) {
                            // Session management handled by WebSocket routing finally block
                        }
                    }
                }
            }
            delay(tickSeconds * 1000L)
        }

        // Apply Oscillate vs Loop Logic
        if (config.oscillate) {
            println("Vehicle $id reversing for return leg.")
            currentRoute = currentRoute.copy(points = currentRoute.points.reversed())
        } else {
            println("Vehicle $id looping back to start (One-way).")
            // No point reversal needed for one-way streets
        }
        delay(2000L)
    }
}

// Helper function to send the count to all active browsers
private suspend fun broadcastSessionCount() {
    val message = jsonMapper.writeValueAsString(mapOf(
        "type" to "SESSION_COUNT",
        "count" to sessions.size
    ))

    synchronized(sessions) {
        sessions.forEach { session ->
            // Use the session's own scope or launch a new one to avoid blocking
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    session.send(message)
                } catch (e: Exception) {
                    // Session might have closed during broadcast
                }
            }
        }
    }
}