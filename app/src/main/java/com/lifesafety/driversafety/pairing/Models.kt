package com.lifesafety.driversafety.pairing

import com.lifesafety.driversafety.settings.DriverSettings

/** Cloud Functions are deployed in Mumbai. The app must call the same region. */
const val FUNCTIONS_REGION = "asia-south1"

enum class LinkRole(val wireName: String) {
    PRIMARY("primary"),
    SECONDARY("secondary");

    companion object {
        fun fromWire(value: String?): LinkRole? = entries.firstOrNull { it.wireName == value }
    }
}

enum class LinkStatus(val wireName: String) {
    PENDING_CONSENT("pending_consent"),
    ACTIVE("active");

    companion object {
        fun fromWire(value: String?): LinkStatus? = entries.firstOrNull { it.wireName == value }
    }
}

/** One driver-admin pair. Mirrors links/{driverId}_{adminId} in Firestore. */
data class Link(
    val id: String,
    val driverId: String,
    val adminId: String,
    val role: LinkRole,
    val status: LinkStatus,
    val driverName: String,
    val adminName: String
)

/** The driver's record, drivers/{driverId}. Admins can read it only after the driver agreed. */
data class DriverRecord(
    val id: String,
    val displayName: String,
    val linkStatus: String,
    val primaryAdminId: String?,
    val primaryAdminName: String?,
    val secondaryAdminId: String?,
    val secondaryAdminName: String?,
    val secondaryStatus: String,
    val settings: DriverSettings = DriverSettings.DEFAULT
)

data class PairingCode(
    val code: String,
    val expiresAtMillis: Long
)
