package com.pjmbusnel.smartbikes.simulator

import com.pjmbusnel.smartbikes.io.ItineraryLoader
import com.pjmbusnel.smartbikes.model.Coordinate
import com.pjmbusnel.smartbikes.model.DirectedSegment
import com.pjmbusnel.smartbikes.model.Route
import com.pjmbusnel.smartbikes.model.TripDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MovementEngineBoundaryTest {

    private val loader = ItineraryLoader()

    /**
     * Zero-Length Segments:
     * Tests that the engine handles segments where two points are identical.
     * This ensures interpolate() doesn't cause NaN or division by zero.
     */
    @Test
    fun `test zero-length segment handling`() {
        val sameCoord = Coordinate(18.0, 98.0)
        val route = Route("r-zero", "Zero Length", listOf(sameCoord, sameCoord))
        val engine = MovementEngine(route, 60.0)

        // Act: A tick that would normally move the bike
        val result = engine.tick(1)

        // Assert: Distance is 0, so it should snap to the end and finish
        assertEquals(sameCoord, result)
        assertTrue(engine.isFinished())
    }

    /**
     * Single-Point Routes:
     * If a route accidentally contains only one point, it should be finished immediately.
     */
    @Test
    fun `test single-point route behavior`() {
        val coord = Coordinate(18.0, 98.0)
        val route = Route("r-single", "Single Point", listOf(coord))
        val engine = MovementEngine(route, 60.0)

        assertTrue(engine.isFinished(), "A route with 1 point has no segments to travel")
        assertEquals(coord, engine.tick(1), "Tick should simply return the only point available")
    }

    /**
     * Empty Segment Lists in Trip Assembly:
     * Tests ItineraryLoader behavior when a trip points to IDs that don't exist.
     */
    @Test
    fun `test assembleRoute with non-existent segment IDs`() {
        val tripDef = TripDefinition("t-error", "Broken Trip", listOf(
            DirectedSegment("non-existent-id")
        ))

        // Loader should return a Route with an empty points list
        val route = loader.assembleRoute(tripDef, emptyList(), tripReverse = false)

        assertTrue(route.points.isEmpty(), "Route should have no points if segments are missing")
    }

    /**
     * Movement Engine with Empty Route:
     * Verifies that initializing the engine with an empty list (invalid state)
     * is handled gracefully or caught.
     */
    @Test
    fun `test movement engine with empty route points`() {
        val emptyRoute = Route("r-empty", "Empty", emptyList())

        // This will likely throw a NoSuchElementException because of .first() in init
        assertThrows<NoSuchElementException> {
            MovementEngine(emptyRoute, 60.0)
        }
    }
}