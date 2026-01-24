package com.pjmbusnel.smartbikes

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pjmbusnel.smartbikes.io.ItineraryLoader
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Collections.synchronizedSet
import com.pjmbusnel.smartbikes.model.Route as BikeRoute

// Global state for WebSocket sessions
private val jsonMapper = jacksonObjectMapper()
private val sessions = synchronizedSet(mutableSetOf<WebSocketServerSession>())
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

fun main() {
    // 1. Start Ktor 3.x Server
    val server = embeddedServer(factory = Netty, port = 8080) {
        install(WebSockets)
        routing {
            webSocket("/fleet") {
                // In Ktor 3.x, 'this' refers to WebSocketServerSession
                sessions.add(this)
                try {
                    for (frame in incoming) { /* Keep alive */ }
                } catch (e: Exception) {
                    println("Connection closed: ${e.message}")
                } finally {
                    sessions.remove(this)
                }
            }
        }
    }.start(wait = false)

    runBlocking {
        val loader = ItineraryLoader()
        val routes = loader.loadAssembledRoutes()

        val baseRoute = routes.find { it.id == "trip-full-ring" }
            ?: throw IllegalStateException("Trip 'trip-full-ring' not found!")

        println("--- Starting Simulation (Ktor 3.x + K2) ---")
        val tickSeconds = 5

        val forwardJob = launch {
            delay((0..5000).random().toLong())
            runVehicle("B-RING-FWD", baseRoute, VehicleType.BIKE, tickSeconds, this)
        }

        val reverseRoute = baseRoute.copy(
            id = "${baseRoute.id}-REV",
            points = baseRoute.points.reversed()
        )

        val reverseJob = launch {
            delay((0..5000).random().toLong())
            runVehicle("M-RING-REV", reverseRoute, VehicleType.MOTORBIKE, tickSeconds, this)
        }

        joinAll(forwardJob, reverseJob)
        server.stop(1000, 2000)
    }
}

suspend fun runVehicle(
    id: String,
    route: BikeRoute,
    type: VehicleType,
    tickSeconds: Int,
    scope: CoroutineScope // We pass the scope from main
) {
    val engine = MovementEngine(route, type.baseSpeedKph)

    while (!engine.isFinished()) {
        val newPos = engine.tick(tickSeconds)
        val timeLabel = LocalTime.now().format(timeFormatter)

        val telemetry = Telemetry(
            bikeId = id,
            vehicleType = type.name,
            lat = newPos.lat,
            lng = newPos.lng,
            speedKph = engine.currentSpeedKph
        )

        val json = jsonMapper.writeValueAsString(telemetry)
        println("[$timeLabel] $json")

        // --- THE FIX IS HERE ---
        // We use the scope passed from runBlocking to launch the broadcast
        synchronized(sessions) {
            sessions.toList().forEach { session ->
                scope.launch { // Explicitly use the passed scope
                    try {
                        session.send(json)
                    } catch (e: Exception) {
                        // Session likely closed, handled by finally block in main
                        println("Exception caught: $e")
                    }
                }
            }
        }

        delay(tickSeconds * 1000L)
    }
}