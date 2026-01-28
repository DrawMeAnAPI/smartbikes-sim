package com.pjmbusnel.smartbikes.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Coordinate(
    val lat: Double,
    val lng: Double
)

/**
 * Represents a sequence of coordinates forming a delivery path.
 */
data class Route(
    val id: String,
    val name: String,
    val points: List<Coordinate>
)

/**
 * Represents a single reusable path between two points.
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
data class Segment(
    val id: String,
    val points: List<Coordinate>
)

/**
 * A logical delivery route made by chaining multiple segment IDs.
 */
data class TripDefinition(
    val id: String,
    val name: String,
    @param:JsonProperty("segmentIds") val segmentIds: List<String>
)

/**
 * The top-level container for our JSON file.
 */
data class ItineraryLibrary(
    val segments: List<Segment>,
    val trips: List<TripDefinition>
)