package com.lifesafety.driversafety.trip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEndDetectorTest {

    private val minute = 60_000L

    @Test
    fun endsAfterBeingStationaryForTheConfiguredMinutes() {
        val d = AutoEndDetector()
        d.start(0)
        d.onFix(40.0, 1 * minute)
        d.onFix(0.0, 2 * minute)
        assertFalse(d.shouldEnd(16 * minute, 15))
        assertTrue(d.shouldEnd(17 * minute, 15))
    }

    @Test
    fun movingAgainResetsTheTimer() {
        val d = AutoEndDetector()
        d.start(0)
        d.onFix(0.0, 1 * minute)
        d.onFix(0.0, 10 * minute)
        d.onFix(20.0, 11 * minute)
        d.onFix(0.0, 12 * minute)
        assertFalse(d.shouldEnd(20 * minute, 15))
        assertTrue(d.shouldEnd(27 * minute, 15))
    }

    @Test
    fun endsWhenNoFixArrivesForTheConfiguredMinutes() {
        val d = AutoEndDetector()
        d.start(0)
        d.onFix(50.0, 1 * minute)
        assertFalse(d.shouldEnd(15 * minute, 15))
        assertTrue(d.shouldEnd(16 * minute, 15))
    }

    @Test
    fun poorAccuracyFixesThatMoveKeepTheTripAlive() {
        val d = AutoEndDetector()
        d.start(0)
        // No accurate fix for 20 minutes, but 40 m fixes that move about 500 m every minute.
        for (minuteIndex in 0..20) {
            d.onRawFix(28.6 + minuteIndex * 0.0045, 77.2, 40f, minuteIndex * minute)
        }
        assertFalse(d.shouldEnd(20 * minute, 15))
    }

    @Test
    fun poorAccuracyFixesThatJitterDoNotKeepTheTripAlive() {
        val d = AutoEndDetector()
        d.start(0)
        for (minuteIndex in 0..20) {
            d.onRawFix(28.6 + (minuteIndex % 2) * 0.0002, 77.2, 40f, minuteIndex * minute) // about 22 m back and forth
        }
        assertTrue(d.shouldEnd(16 * minute, 15))
    }

    @Test
    fun slowCrawlBelowThreeKmhCountsAsStationary() {
        val d = AutoEndDetector()
        d.start(0)
        d.onFix(2.0, 0)
        d.onFix(1.0, 5 * minute)
        assertTrue(d.shouldEnd(15 * minute, 15))
    }
}
