package com.lifesafety.driversafety.trip

import kotlin.math.max

/** What the trip service must do after feeding a speed reading into [OverspeedStateMachine]. */
sealed interface OverspeedAction {
    /** Start the beep and voice on the driver's phone and turn the screen red. */
    data object StartAlarm : OverspeedAction

    /** Stop the beep and voice, screen back to normal. */
    data object StopAlarm : OverspeedAction

    /** Still over the limit after the admin alert delay: write the "Overspeed started" event and alert both admins. */
    data class AlertAdmins(val speedKmh: Double, val topSpeedKmh: Double) : OverspeedAction

    /** Back under the limit after the admins were alerted: write "Back to normal" with top speed and duration. */
    data class BackToNormal(val topSpeedKmh: Double, val durationSec: Int) : OverspeedAction

    /** Over the limit long enough for the alarm but not for the admins. Saved in the trip record only. */
    data class ShortOverspeed(val topSpeedKmh: Double, val durationSec: Int) : OverspeedAction
}

/**
 * The overspeed rules from the product spec, as a small state machine:
 *
 *  NORMAL        speed goes above limit + tolerance            -> OVER_PENDING (remember when it started)
 *  OVER_PENDING  back under before 3 s                         -> NORMAL (a blip, nothing recorded)
 *                still over after 3 s                          -> ALARM, action StartAlarm
 *  ALARM         still over for the admin alert delay          -> ALERTED, action AlertAdmins
 *  ALARM/ALERTED at or below the limit for 5 s                 -> NORMAL, actions StopAlarm + ShortOverspeed or BackToNormal
 *
 * "Over" means above limit + tolerance and is what starts an episode. "Under" means at or below the plain
 * limit and is what ends it. A speed inside the tolerance band (above the limit, within the tolerance) keeps
 * the alarm going and still counts as "over the limit" for the admin alert, as the spec says
 * ("still over the limit after the admin alert delay"). Durations use the time the overspeed began.
 * Times are plain milliseconds from any monotonic clock; the caller passes them in, so tests need no clock.
 */
class OverspeedStateMachine(
    private val alarmDelayMs: Long = 3_000L,
    private val recoverDelayMs: Long = 5_000L
) {
    enum class Phase { NORMAL, OVER_PENDING, ALARM, ALERTED }

    var phase: Phase = Phase.NORMAL
        private set

    /** Highest speed seen during the current overspeed episode. */
    var topSpeedKmh: Double = 0.0
        private set

    private var overSinceMs = 0L
    private var alarmSinceMs = 0L
    private var underSinceMs: Long? = null

    val isAlarmOn: Boolean get() = phase == Phase.ALARM || phase == Phase.ALERTED
    val adminsAlerted: Boolean get() = phase == Phase.ALERTED

    fun update(
        speedKmh: Double,
        limitKmh: Int,
        toleranceKmh: Int,
        adminAlertDelayMs: Long,
        nowMs: Long
    ): List<OverspeedAction> {
        val actions = mutableListOf<OverspeedAction>()
        val over = speedKmh > limitKmh + toleranceKmh
        val under = speedKmh <= limitKmh
        when (phase) {
            Phase.NORMAL -> if (over) {
                overSinceMs = nowMs
                topSpeedKmh = speedKmh
                phase = Phase.OVER_PENDING
            }

            Phase.OVER_PENDING -> if (!over) {
                phase = Phase.NORMAL
            } else {
                topSpeedKmh = max(topSpeedKmh, speedKmh)
                if (nowMs - overSinceMs >= alarmDelayMs) {
                    phase = Phase.ALARM
                    alarmSinceMs = nowMs
                    underSinceMs = null
                    actions += OverspeedAction.StartAlarm
                }
            }

            Phase.ALARM, Phase.ALERTED -> {
                topSpeedKmh = max(topSpeedKmh, speedKmh)
                if (under) {
                    val since = underSinceMs ?: nowMs.also { underSinceMs = it }
                    if (nowMs - since >= recoverDelayMs) {
                        val durationSec = ((since - overSinceMs) / 1000L).toInt()
                        actions += OverspeedAction.StopAlarm
                        actions += if (phase == Phase.ALERTED) {
                            OverspeedAction.BackToNormal(topSpeedKmh, durationSec)
                        } else {
                            OverspeedAction.ShortOverspeed(topSpeedKmh, durationSec)
                        }
                        phase = Phase.NORMAL
                    }
                } else {
                    underSinceMs = null
                    // Not under the limit: the alarm keeps going and the admin alert delay keeps running.
                    if (phase == Phase.ALARM && nowMs - alarmSinceMs >= adminAlertDelayMs) {
                        phase = Phase.ALERTED
                        actions += OverspeedAction.AlertAdmins(speedKmh, topSpeedKmh)
                    }
                }
            }
        }
        return actions
    }

    /** Trip ended (or GPS gone for good): close any open episode and return the actions that finish it. */
    fun reset(nowMs: Long): List<OverspeedAction> {
        val actions = mutableListOf<OverspeedAction>()
        if (isAlarmOn) {
            val endMs = underSinceMs ?: nowMs
            val durationSec = ((endMs - overSinceMs) / 1000L).toInt()
            actions += OverspeedAction.StopAlarm
            actions += if (phase == Phase.ALERTED) {
                OverspeedAction.BackToNormal(topSpeedKmh, durationSec)
            } else {
                OverspeedAction.ShortOverspeed(topSpeedKmh, durationSec)
            }
        }
        phase = Phase.NORMAL
        underSinceMs = null
        topSpeedKmh = 0.0
        return actions
    }
}
