package com.lifesafety.driversafety.ui

import android.content.Context
import com.lifesafety.driversafety.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Dates, times, durations and speeds as the screens show them. Times use the time zone the event happened in. */
object TimeFormat {

    /** "Tue, 8 Oct 2026, 3:45 pm" in the zone the event was recorded in (falls back to the phone's zone). */
    fun dateTime(timestampUtc: Long, timezoneId: String?): String {
        val format = SimpleDateFormat("EEE, d MMM yyyy, h:mm a", Locale.getDefault())
        format.timeZone = zone(timezoneId)
        return format.format(Date(timestampUtc))
    }

    /** "3:45 pm" */
    fun time(timestampUtc: Long, timezoneId: String?): String {
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        format.timeZone = zone(timezoneId)
        return format.format(Date(timestampUtc))
    }

    /** "8 Oct, 3:45 pm" (short, for lists) */
    fun shortDateTime(timestampUtc: Long, timezoneId: String?): String {
        val format = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
        format.timeZone = zone(timezoneId)
        return format.format(Date(timestampUtc))
    }

    /** "Friday, 10 October" */
    fun longDate(timestampUtc: Long): String =
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(timestampUtc))

    /** "Today", "Yesterday" or "Wed, 8 Oct", in the phone's zone, for grouping lists by day. */
    fun dayLabel(context: Context, timestampUtc: Long, nowUtc: Long = System.currentTimeMillis()): String {
        val dayFormat = SimpleDateFormat("yyyyDDD", Locale.US)
        val day = dayFormat.format(Date(timestampUtc))
        val today = dayFormat.format(Date(nowUtc))
        val yesterday = dayFormat.format(Date(nowUtc - 86_400_000L))
        return when (day) {
            today -> context.getString(R.string.day_today)
            yesterday -> context.getString(R.string.day_yesterday)
            else -> SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(timestampUtc))
        }
    }

    /** "just now", "4 min ago", "2 h ago", "3 d ago" */
    fun ago(context: Context, timestampUtc: Long, nowUtc: Long = System.currentTimeMillis()): String {
        val sec = ((nowUtc - timestampUtc) / 1000L).coerceAtLeast(0)
        return when {
            sec < 60 -> context.getString(R.string.time_just_now)
            sec < 3600 -> context.getString(R.string.time_minutes_ago, (sec / 60).toInt())
            sec < 86_400 -> context.getString(R.string.time_hours_ago, (sec / 3600).toInt())
            else -> context.getString(R.string.time_days_ago, (sec / 86_400).toInt())
        }
    }

    /** "45 s", "12 min", "1 h 05 min" */
    fun duration(context: Context, totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> context.getString(R.string.time_hours_minutes, h, m)
            m > 0 -> context.getString(R.string.time_minutes, m)
            else -> context.getString(R.string.time_seconds, s)
        }
    }

    /** Whole km/h, Latin digits everywhere so the number reads the same in Hindi. */
    fun kmh(value: Double): String = String.format(Locale.US, "%.0f", value)

    fun km(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun zone(timezoneId: String?): TimeZone =
        timezoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
}
