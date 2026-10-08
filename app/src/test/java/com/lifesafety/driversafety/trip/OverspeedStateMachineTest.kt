package com.lifesafety.driversafety.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverspeedStateMachineTest {

    private val limit = 60
    private val tolerance = 0
    private val adminDelayMs = 10_000L

    private fun OverspeedStateMachine.feed(speed: Double, atMs: Long) =
        update(speed, limit, tolerance, adminDelayMs, atMs)

    @Test
    fun blipShorterThanThreeSecondsIsIgnored() {
        val m = OverspeedStateMachine()
        assertTrue(m.feed(70.0, 0).isEmpty())
        assertTrue(m.feed(70.0, 2_000).isEmpty())
        assertTrue(m.feed(50.0, 2_500).isEmpty())
        assertEquals(OverspeedStateMachine.Phase.NORMAL, m.phase)
        assertFalse(m.isAlarmOn)
    }

    @Test
    fun alarmStartsAfterThreeSecondsOverTheLimit() {
        val m = OverspeedStateMachine()
        m.feed(70.0, 0)
        assertTrue(m.feed(70.0, 2_999).isEmpty())
        assertEquals(listOf(OverspeedAction.StartAlarm), m.feed(70.0, 3_000))
        assertTrue(m.isAlarmOn)
        assertFalse(m.adminsAlerted)
    }

    @Test
    fun shortOverspeedStopsAlarmWithoutAlertingAdmins() {
        val m = OverspeedStateMachine()
        m.feed(70.0, 0)
        m.feed(70.0, 3_000) // alarm
        m.feed(75.0, 5_000)
        m.feed(55.0, 6_000) // under since 6 s
        assertTrue(m.feed(55.0, 10_000).isEmpty())
        val actions = m.feed(55.0, 11_000)
        assertEquals(OverspeedAction.StopAlarm, actions[0])
        assertEquals(OverspeedAction.ShortOverspeed(75.0, 6), actions[1])
        assertEquals(OverspeedStateMachine.Phase.NORMAL, m.phase)
    }

    @Test
    fun adminsAreAlertedAfterTheDelayAndToldWhenBackToNormal() {
        val m = OverspeedStateMachine()
        m.feed(70.0, 0)
        m.feed(70.0, 3_000) // alarm at 3 s
        assertTrue(m.feed(80.0, 12_999).isEmpty())
        val alert = m.feed(80.0, 13_000) // 10 s after the alarm began
        assertEquals(listOf(OverspeedAction.AlertAdmins(80.0, 80.0)), alert)
        assertTrue(m.adminsAlerted)
        m.feed(90.0, 20_000)
        m.feed(60.0, 30_000) // at the limit counts as under
        val back = m.feed(58.0, 35_000)
        assertEquals(OverspeedAction.StopAlarm, back[0])
        assertEquals(OverspeedAction.BackToNormal(90.0, 30), back[1])
        assertEquals(OverspeedStateMachine.Phase.NORMAL, m.phase)
    }

    @Test
    fun dippingUnderBrieflyDoesNotStopTheAlarm() {
        val m = OverspeedStateMachine()
        m.feed(70.0, 0)
        m.feed(70.0, 3_000)
        m.feed(58.0, 4_000) // under for 3 s only
        m.feed(70.0, 7_000) // over again resets the recovery timer
        assertTrue(m.feed(58.0, 8_000).isEmpty())
        assertTrue(m.feed(58.0, 12_000).isEmpty())
        assertEquals(OverspeedAction.StopAlarm, m.feed(58.0, 13_000)[0])
    }

    @Test
    fun toleranceBandKeepsAlarmGoing() {
        val m = OverspeedStateMachine()
        // limit 60, tolerance 5: over means > 65, under means <= 60
        m.update(70.0, 60, 5, adminDelayMs, 0)
        m.update(70.0, 60, 5, adminDelayMs, 3_000)
        assertTrue(m.isAlarmOn)
        // 63 is inside the band: not over, not under, alarm stays and the recovery timer does not run
        assertTrue(m.update(63.0, 60, 5, adminDelayMs, 4_000).isEmpty())
        assertTrue(m.update(63.0, 60, 5, adminDelayMs, 20_000).isEmpty())
        assertTrue(m.isAlarmOn)
        assertFalse(m.adminsAlerted)
    }

    @Test
    fun resetClosesAnOpenEpisode() {
        val m = OverspeedStateMachine()
        m.feed(70.0, 0)
        m.feed(70.0, 3_000)
        m.feed(80.0, 13_000) // alerted
        val actions = m.reset(20_000)
        assertEquals(OverspeedAction.StopAlarm, actions[0])
        assertEquals(OverspeedAction.BackToNormal(80.0, 20), actions[1])
        assertEquals(OverspeedStateMachine.Phase.NORMAL, m.phase)
        assertTrue(m.reset(21_000).isEmpty())
    }
}
