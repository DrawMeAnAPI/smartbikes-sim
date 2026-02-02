package com.pjmbusnel.smartbikes.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Coordinate(
    val lat: Double,
    val lng: Double,
    val name: String? = null
)

data class Route(
    val id: String,
    val name: String,
    val points: List<Coordinate>
)

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
data class Segment(
    val id: String,
    val name: String? = null,
    val points: List<Coordinate>
)

data class DirectedSegment(
    val segmentId: String,
    val reversed: Boolean = false // If true, reverse this specific segment
)

data class TripDefinition(
    val id: String,
    val name: String,
    val directedSegments: List<DirectedSegment> // Replaces List<String>
)

data class ItineraryLibrary(
    val segments: List<Segment>,
    val trips: List<TripDefinition>
)

data class BikeTripConfig(
    val id: String,
    val type: String, // "BIKE" or "MOTORBIKE"
    val tripId: String,
    val reverse: Boolean = false,
    val oscillate: Boolean = true // If false, the vehicle respawns at the start (one-way)
)