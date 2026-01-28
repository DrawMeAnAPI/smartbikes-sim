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
import java.time.format.DateTimeFormatter
import java.util.Collections.synchronizedSet
import kotlin.time.Clock
import com.pjmbusnel.smartbikes.model.Route as BikeRoute

// Global state for WebSocket sessions
private val jsonMapper = jacksonObjectMapper()
private val sessions = synchronizedSet(mutableSetOf<WebSocketServerSession>())
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

fun main() {
    val server = embeddedServer(factory = Netty, port = 8080) {
        install(WebSockets)
        routing {
            webSocket("/fleet") {
                sessions.add(this) // Ktor WebSocketServerSession
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

        println("--- Starting Simulation ---")
        val tickSeconds = 5

        // We create a list to track all our active jobs
        val allJobs = mutableListOf<Job>()

        routes.forEachIndexed { index, baseRoute ->
            println("Launching fleet for trip: ${baseRoute.name}")

            // 1. Launch Forward Vehicle for this specific trip
            allJobs += launch {
                // Give each trip a tiny staggered start so the logs are readable
                delay(index * 500L)
                runVehicle("F-${baseRoute.id}", baseRoute, VehicleType.BIKE, tickSeconds, this)
            }

            // 2. Create and Launch Reverse Vehicle for this specific trip
            val reverseRoute = baseRoute.copy(
                id = "${baseRoute.id}-REV",
                points = baseRoute.points.reversed()
            )

            allJobs += launch {
                delay(index * 1000L + 500) // Slight offset from the forward vehicle
                runVehicle("R-${baseRoute.id}", reverseRoute, VehicleType.MOTORBIKE, tickSeconds, this)
            }
        }

        allJobs.joinAll()
        println("All vehicles have reached their destinations.")
    }
}

suspend fun runVehicle(
    id: String,
    initialRoute: BikeRoute,
    initialType: VehicleType,
    tickSeconds: Int,
    scope: CoroutineScope
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

        println("Vehicle $id reached end of leg. Reversing and swapping type...")
        currentRoute = currentRoute.copy(
            points = currentRoute.points.reversed()
        )
        delay(2000L)
    }
}