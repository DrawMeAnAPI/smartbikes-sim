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
    fun assembleRoute(tripDef: TripDefinition, allSegments: List<Segment>, tripReverse: Boolean = false): Route {
        // 1. If the WHOLE TRIP is reversed (Return leg), reverse the segment sequence
        val directedSegments = if (tripReverse) tripDef.directedSegments.reversed() else tripDef.directedSegments
        val allPoints = mutableListOf<Coordinate>()

        directedSegments.forEach { directedSeg ->
            val segment = allSegments.find { it.id == directedSeg.segmentId }
            if (segment != null) {
                // 2. Logic: Should this specific segment be flipped?
                // A segment is flipped if (it's marked reverse in the trip) XOR (the whole trip is a return leg)
                val shouldFlipSegment = directedSeg.reversed xor tripReverse

                val points = if (shouldFlipSegment) segment.points.reversed() else segment.points

                if (allPoints.isEmpty()) {
                    allPoints.addAll(points)
                } else {
                    // TODO is it still necessary ??
                    allPoints.addAll(points.drop(1)) // Stitching point
                }
            }
        }
        return Route(tripDef.id, tripDef.name, allPoints)
    }

    /**
     * Performs a "Smoke Test" to ensure all JSON files are internally consistent.
     * Throws an IllegalStateException if a mismatch is found.
     */
    fun validateIntegrity(library: ItineraryLibrary, fleet: List<BikeTripConfig>) {
        val tripIds = library.trips.map { it.id }.toSet()
        val segmentIds = library.segments.map { it.id }.toSet()

        fleet.forEach { bike ->
            // 1. Check if the tripId exists
            if (!tripIds.contains(bike.tripId)) {
                throw IllegalStateException("Integrity Error: Bike '${bike.id}' refers to non-existent trip '${bike.tripId}'")
            }

            // 2. Check if all segments within that trip exist
            val trip = library.trips.find { it.id == bike.tripId }
            trip?.directedSegments?.forEach { dirSeg ->
                if (!segmentIds.contains(dirSeg.segmentId)) {
                    throw IllegalStateException("Integrity Error: Trip '${trip.id}' refers to non-existent segment '${dirSeg.segmentId}'")
                }
            }
        }
        println("--- Integrity Check Passed: Fleet and Infrastructure are synced ---")
    }
}