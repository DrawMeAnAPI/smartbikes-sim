package com.pjmbusnel.smartbikes.io

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pjmbusnel.smartbikes.model.*
import java.io.InputStream

class ItineraryLoader {
    private val mapper = jacksonObjectMapper()

    /**
     * Loads the entire library and returns the assembled routes.
     */
    fun loadAssembledRoutes(): List<Route> {
        val inputStream: InputStream? = javaClass.classLoader.getResourceAsStream("itineraries.json")
            ?: throw IllegalStateException("itineraries.json not found in resources!")

        val library: ItineraryLibrary = try {
            mapper.readValue(inputStream!!)
        } catch (e: Exception) {
            println("Error parsing JSON: ${e.message}")
            return emptyList()
        }

        return assembleTrips(library)
    }

    private fun assembleTrips(library: ItineraryLibrary): List<Route> {
        return library.trips.map { tripDef ->
            val allPoints = mutableListOf<Coordinate>()

            tripDef.segmentIds.forEach { segId ->
                val segment = library.segments.find { it.id == segId }
                if (segment != null) {
                    // To avoid duplicating the meeting point of two segments,
                    // we drop the first point of subsequent segments.
                    if (allPoints.isEmpty()) {
                        allPoints.addAll(segment.points)
                    } else {
                        allPoints.addAll(segment.points.drop(1))
                    }
                }
            }

            Route(tripDef.id, tripDef.name, allPoints)
        }
    }
}