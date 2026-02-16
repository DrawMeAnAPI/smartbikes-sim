package com.pjmbusnel.smartbikes.io

import com.pjmbusnel.smartbikes.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ItineraryLoaderTest {

    private val loader = ItineraryLoader()

    // Test Data
    private val segA = Segment("seg-A", "Segment A", listOf(
        Coordinate(10.0, 10.0),
        Coordinate(10.0, 11.0)
    ))

    private val segB = Segment("seg-B", "Segment B", listOf(
        Coordinate(10.0, 11.0),
        Coordinate(10.0, 12.0)
    ))

    private val segC = Segment("seg-C", "Segment C", listOf(
        Coordinate(10.0, 12.0),
        Coordinate(10.0, 13.0)
    ))

    // --- 1. Basic Assembly & Stitching ---

    @Test
    fun `test assembleRoute stitches points correctly`() {
        val trip = TripDefinition("t1", "Test", listOf(
            DirectedSegment("seg-A"),
            DirectedSegment("seg-B")
        ))
        val route = loader.assembleRoute(trip, listOf(segA, segB), tripReverse = false)

        // Total points: 2 + 2 - 1 (stitched) = 3
        assertEquals(3, route.points.size)
        assertEquals(Coordinate(10.0, 10.0), route.points.first())
        assertEquals(Coordinate(10.0, 12.0), route.points.last())
    }

    @Test
    fun `test assembleRoute stitches multiple segments without duplicates`() {
        val trip = TripDefinition("t-multi", "Multi", listOf(
            DirectedSegment("seg-A"),
            DirectedSegment("seg-B"),
            DirectedSegment("seg-C")
        ))
        val route = loader.assembleRoute(trip, listOf(segA, segB, segC), tripReverse = false)

        // Verifies the drop(1) logic works across multiple junctions
        assertEquals(4, route.points.size)
        assertEquals(Coordinate(10.0, 13.0), route.points.last())
    }

    // --- 2. Direction & XOR Logic ---

    @Test
    fun `test assembleRoute return trip XOR logic`() {
        val trip = TripDefinition("t3", "Full Trip", listOf(
            DirectedSegment("seg-A", reversed = false),
            DirectedSegment("seg-B", reversed = false)
        ))
        // Start should be the end of Seg B (10.0, 12.0)
        val route = loader.assembleRoute(trip, listOf(segA, segB), tripReverse = true)

        assertEquals(Coordinate(10.0, 12.0), route.points.first())
        assertEquals(Coordinate(10.0, 10.0), route.points.last())
    }

    @Test
    fun `test assembleRoute complex XOR logic with mixed directions`() {
        // Seg B naturally is 11->12. We want a trip driving A(10->11) then B-reversed(12->11)
        val trip = TripDefinition("t-mixed", "Mixed", listOf(
            DirectedSegment("seg-A", reversed = false),
            DirectedSegment("seg-B", reversed = true)
        ))

        // Forward: A(10->11) + B-flipped(12->11). Ends at 11.
        val forward = loader.assembleRoute(trip, listOf(segA, segB), tripReverse = false)
        assertEquals(Coordinate(10.0, 11.0), forward.points.last())

        // Return (Reverse): Everything flips. B is now 11->12, A is 11->10
        val returnRoute = loader.assembleRoute(trip, listOf(segA, segB), tripReverse = true)
        assertEquals(Coordinate(10.0, 11.0), returnRoute.points.first())
        assertEquals(Coordinate(10.0, 10.0), returnRoute.points.last())
    }

    // --- 3. Robustness & Error Handling ---

    @Test
    fun `test assembleRoute handles missing segment IDs gracefully`() {
        val trip = TripDefinition("t-missing", "Broken", listOf(
            DirectedSegment("seg-A"),
            DirectedSegment("NON_EXISTENT"),
            DirectedSegment("seg-B")
        ))
        // Should skip the missing one and keep the others
        val route = loader.assembleRoute(trip, listOf(segA, segB), tripReverse = false)
        assertEquals(3, route.points.size)
    }

    @Test
    fun `test assembleRoute with empty directedSegments`() {
        val emptyTrip = TripDefinition("t-empty", "Empty", emptyList())
        val route = loader.assembleRoute(emptyTrip, listOf(segA, segB), tripReverse = false)
        assertTrue(route.points.isEmpty()) // Boundary case check
    }

    /**
     * Multi-Segment "Return" Leg:
     * Verifies that for a 3-segment trip (A -> B -> C), the return leg (tripReverse = true)
     * correctly orders the segments as [C-reversed, B-reversed, A-reversed].
     */
    @Test
    fun `test multi-segment return leg coordinate order`() {
        val segA = Segment("A", points = listOf(Coordinate(10.0, 10.0), Coordinate(10.0, 11.0)))
        val segB = Segment("B", points = listOf(Coordinate(10.0, 11.0), Coordinate(10.0, 12.0)))
        val segC = Segment("C", points = listOf(Coordinate(10.0, 12.0), Coordinate(10.0, 13.0)))

        val trip = TripDefinition("t-multi", "Multi-Trip", listOf(
            DirectedSegment("A"), DirectedSegment("B"), DirectedSegment("C")
        ))

        // Act: Generate return leg
        val route = loader.assembleRoute(trip, listOf(segA, segB, segC), tripReverse = true)

        // Assert: Correct sequence: End of C -> Start of C -> Start of B -> Start of A
        // (10.13) -> (10.12) -> (10.11) -> (10.10)
        assertEquals(4, route.points.size)
        assertEquals(Coordinate(10.0, 13.0), route.points[0]) // End of C
        assertEquals(Coordinate(10.0, 12.0), route.points[1]) // Junction C/B
        assertEquals(Coordinate(10.0, 11.0), route.points[2]) // Junction B/A
        assertEquals(Coordinate(10.0, 10.0), route.points[3]) // Start of A
    }

    /**
     * Stitching Integrity:
     * Verifies that if Segment A ends at (1.0, 1.0) and Segment B starts at (1.0, 1.0),
     * the join logic (drop(1)) prevents coordinate duplication.
     */
    @Test
    fun `test stitching integrity avoids duplicate junction points`() {
        val segA = Segment("A", points = listOf(Coordinate(1.0, 1.0), Coordinate(2.0, 2.0), Coordinate(3.0, 3.0)))
        val segB = Segment("B", points = listOf(Coordinate(3.0, 3.0), Coordinate(4.0, 4.0), Coordinate(5.0, 5.0)))

        val trip = TripDefinition("t-stitch", "Stitch", listOf(DirectedSegment("A"), DirectedSegment("B")))

        // Act
        val route = loader.assembleRoute(trip, listOf(segA, segB), tripReverse = false)

        // Assert: Should have 5 points (3 from A + 2 from B), not 6.
        assertEquals(5, route.points.size)
        // Verify middle point is only present once
        assertEquals(Coordinate(3.0, 3.0), route.points[2])
        assertEquals(Coordinate(4.0, 4.0), route.points[3])
    }

    /**
     * Oscillation Flip:
     * Verifies that the Route model can be correctly reversed for a return leg
     * when the vehicle reaches its destination.
     */
    @Test
    fun `test oscillation flip correctly reverses route points`() {
        val originalPoints = listOf(
            Coordinate(18.0, 98.0),
            Coordinate(18.1, 98.1),
            Coordinate(18.2, 98.2)
        )
        val route = Route("r1", "Oscillation Test", originalPoints)

        // Act: Simulate the flip in runVehicle
        val flippedRoute = route.copy(points = route.points.reversed())

        // Assert
        assertEquals(Coordinate(18.2, 98.2), flippedRoute.points.first())
        assertEquals(Coordinate(18.1, 98.1), flippedRoute.points[1])
        assertEquals(Coordinate(18.0, 98.0), flippedRoute.points.last())
    }
}