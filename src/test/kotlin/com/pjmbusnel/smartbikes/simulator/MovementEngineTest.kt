package com.pjmbusnel.smartbikes.simulator

import com.pjmbusnel.smartbikes.model.Coordinate
import com.pjmbusnel.smartbikes.model.Route
import com.pjmbusnel.smartbikes.utils.PhysicsUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MovementEngineTest {

    @Test
    fun `test engine progression updates public currentPosition`() {
        val start = Coordinate(18.79, 98.98)
        val end = Coordinate(18.80, 98.99)
        val route = Route("test-id", "Test Route", listOf(start, end))
        val engine = MovementEngine(route, 60.0)

        // Verify initial state
        assertEquals(start.lat, engine.currentPosition.lat)

        // Act: Move for a few seconds
        engine.tick(5)

        // Assert: Accessing the public property should now show the new position
        assertNotEquals(start.lat, engine.currentPosition.lat, "Position should have moved from start")
        assertTrue(engine.currentPosition.lat > start.lat, "Position should be progressing toward end")
    }

    /**
     * Fixed Distance Validation:
     * Verifies that the engine moves the vehicle by the correct number of meters
     * relative to the speed reported for that tick.
     */
    @Test
    fun `test fixed distance validation`() {
        // Setup: A long straight route north
        val start = Coordinate(18.0, 98.0)
        val end = Coordinate(18.1, 98.0) // Approx 11.1km away
        val route = Route("r1", "Straight", listOf(start, end))
        val engine = MovementEngine(route, 36.0) // 36 km/h = 10 m/s

        // Act: 1 second tick
        engine.tick(1)

        // The engine fluctuates speed (+/- 10%), so we check against the currentSpeedKph it generated
        val expectedDistance = (engine.currentSpeedKph * 1000.0 / 3600.0) * 1.0
        val actualDistance = PhysicsUtils.calculateDistance(start, engine.currentPosition)

        // Assert precision within 1 millimeter
        assertEquals(expectedDistance, actualDistance, 0.001)
    }

    /**
     * Waypoint Overshoot:
     * Verifies that if a vehicle travels further than the distance to the next waypoint,
     * it "snaps" exactly to that waypoint and does not fly past it.
     */
    @Test
    fun `test waypoint overshoot logic`() {
        // Setup: A very short segment (~1.1 meters)
        val start = Coordinate(18.0, 98.0)
        val end = Coordinate(18.00001, 98.0)
        val route = Route("r2", "Short", listOf(start, end))

        // Speed: 360 km/h = 100 m/s. 1 tick = 100m (covers the 1.1m segment easily)
        val engine = MovementEngine(route, 360.0)

        // Act: 1 second tick
        engine.tick(1)

        // Assert: The engine must land exactly on the destination point
        assertEquals(end.lat, engine.currentPosition.lat)
        assertEquals(end.lng, engine.currentPosition.lng)

        // The engine must report it is finished
        assertTrue(engine.isFinished())
    }

    /**
     * Multi-Waypoint Progression:
     * Verifies that the engine can move through multiple waypoints sequentially.
     * Note: Current logic clears a maximum of one waypoint per tick.
     */
    @Test
    fun `test movement through multiple waypoints`() {
        val p1 = Coordinate(18.0, 98.0)
        val p2 = Coordinate(18.0001, 98.0) // ~11m
        val p3 = Coordinate(18.0002, 98.0) // ~11m
        val route = Route("r3", "Multi", listOf(p1, p2, p3))

        val engine = MovementEngine(route, 360.0) // 100 m/s

        // Tick 1: Clears p1 -> p2
        engine.tick(1)
        assertEquals(p2.lat, engine.currentPosition.lat)
        assertFalse(engine.isFinished())

        // Tick 2: Clears p2 -> p3
        engine.tick(1)
        assertEquals(p3.lat, engine.currentPosition.lat)
        assertTrue(engine.isFinished())
    }
}