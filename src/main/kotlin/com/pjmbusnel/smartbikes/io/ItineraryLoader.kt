package com.pjmbusnel.smartbikes.io

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pjmbusnel.smartbikes.model.*
import java.io.InputStream

class ItineraryLoader {
    private val mapper = jacksonObjectMapper()

    /**
     * Loads the road infrastructure (segments and trip definitions).
     */
    fun loadTrips(): ItineraryLibrary {
        val inputStream: InputStream = javaClass.classLoader.getResourceAsStream("itineraries.json")
            ?: throw IllegalStateException("itineraries.json not found in resources!")
        return mapper.readValue(inputStream)
    }

    /**
     * Loads the fleet configuration (which bike does which trip).
     */
    fun loadFleetConfig(): List<BikeTripConfig> {
        val stream = javaClass.classLoader.getResourceAsStream("bikes-trips.json")
            ?: throw IllegalStateException("bikes-trips.json not found")
        return mapper.readValue(stream)
    }

    /**
     * Logic to stitch segments together into a single continuous Route.
     * Parameter 'reverse' to handle coordinate-level inversion.
     */
    fun assembleRoute(tripDef: TripDefinition, allSegments: List<Segment>, reverse: Boolean = false): Route {
        val segmentIds = if (reverse) tripDef.segmentIds.reversed() else tripDef.segmentIds
        val allPoints = mutableListOf<Coordinate>()

        segmentIds.forEach { segId ->
            val segment = allSegments.find { it.id == segId }
            if (segment != null) {
                // If the trip is reversed, we MUST reverse the points inside the segment
                val points = if (reverse) segment.points.reversed() else segment.points

                if (allPoints.isEmpty()) {
                    allPoints.addAll(points)
                } else {
                    // TODO is it still necessary ??
                    // Stitching: drop the first point of the next segment to avoid duplicates
                    allPoints.addAll(points.drop(1))
                }
            }
        }

        val name = if (reverse) "${tripDef.name} (Return)" else tripDef.name
        return Route(tripDef.id, name, allPoints)
    }
}