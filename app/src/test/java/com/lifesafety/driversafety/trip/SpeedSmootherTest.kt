package com.lifesafety.driversafety.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedSmootherTest {

    @Test
    fun rejectsInaccurateFixesAndFixesWithoutSpeed() {
        val s = SpeedSmoother()
        assertNull(s.accept(50.0, accuracyM = 40f, hasSpeed = true, nowMs = 0))
        assertNull(s.accept(50.0, accuracyM = 10f, hasSpeed = false, nowMs = 0))
        assertNull(s.accept(-1.0, accuracyM = 10f, hasSpeed = true, nowMs = 0))
    }

    @Test
    fun averagesTheLastThreeAcceptedSpeeds() {
        val s = SpeedSmoother()
        assertEquals(30.0, s.accept(30.0, 5f, true, 0)!!, 0.001)
        assertEquals(45.0, s.accept(60.0, 5f, true, 2_000)!!, 0.001)
        assertEquals(60.0, s.accept(90.0, 5f, true, 4_000)!!, 0.001)
        // Window is 3: 60, 90, 120 -> 90
        assertEquals(90.0, s.accept(120.0, 5f, true, 6_000)!!, 0.001)
    }

    @Test
    fun forgetsOldReadingsAfterAGap() {
        val s = SpeedSmoother(staleAfterMs = 10_000)
        s.accept(100.0, 5f, true, 0)
        s.accept(100.0, 5f, true, 2_000)
        // 20 seconds later: the old 100s must not drag the average up.
        assertEquals(10.0, s.accept(10.0, 5f, true, 22_000)!!, 0.001)
    }
}
