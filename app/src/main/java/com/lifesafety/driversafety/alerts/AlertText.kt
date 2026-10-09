package com.lifesafety.driversafety.alerts

import android.content.Context
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.ui.TimeFormat

/**
 * Turns an alert into the words the admin reads, in the phone's language. Used by the push notification
 * (built on the phone from the data message) and by the inbox, so both say the same thing.
 */
object AlertText {

    fun title(context: Context, alert: Alert): String = when (alert.type) {
        AlertType.OVERSPEED_STARTED -> context.getString(R.string.alert_title_overspeed, alert.driverName)
        AlertType.BACK_TO_NORMAL -> context.getString(R.string.alert_title_back_to_normal, alert.driverName)
        AlertType.ADMIN_ADDED -> context.getString(R.string.alert_title_admin_added, alert.driverName)
        AlertType.ADMIN_REMOVED -> context.getString(R.string.alert_title_admin_removed, alert.driverName)
        AlertType.DRIVER_UNLINKED -> context.getString(R.string.alert_title_driver_unlinked, alert.driverName)
        AlertType.UNKNOWN -> alert.driverName
    }

    fun body(context: Context, alert: Alert): String {
        val where = alert.address?.let { " · $it" }.orEmpty()
        val late = if (alert.delayed) " " + context.getString(R.string.alert_delayed_suffix) else ""
        return when (alert.type) {
            AlertType.OVERSPEED_STARTED -> context.getString(
                R.string.alert_body_overspeed,
                alert.speedKmh?.let { TimeFormat.kmh(it) } ?: "?",
                alert.speedLimitKmh ?: 0
            ) + where + late

            AlertType.BACK_TO_NORMAL -> context.getString(
                R.string.alert_body_back_to_normal,
                alert.topSpeedKmh?.let { TimeFormat.kmh(it) } ?: "?",
                TimeFormat.duration(context, alert.durationSec ?: 0)
            ) + where + late

            AlertType.ADMIN_ADDED -> context.getString(R.string.alert_body_admin_added, alert.adminName.orEmpty(), alert.driverName)

            AlertType.ADMIN_REMOVED -> when (alert.reason) {
                "left" -> context.getString(R.string.alert_body_admin_left, alert.adminName.orEmpty(), alert.driverName)
                "primary_removed" -> context.getString(R.string.alert_body_admin_removed_by_primary, alert.byName.orEmpty(), alert.driverName)
                else -> context.getString(R.string.alert_body_admin_removed_by_driver, alert.driverName, alert.adminName.orEmpty())
            }

            AlertType.DRIVER_UNLINKED -> when (alert.reason) {
                "primary_left" -> context.getString(R.string.alert_body_unlinked_primary_left, alert.adminName.orEmpty(), alert.driverName)
                else -> context.getString(R.string.alert_body_unlinked_by_driver, alert.driverName)
            }

            AlertType.UNKNOWN -> ""
        }
    }
}
