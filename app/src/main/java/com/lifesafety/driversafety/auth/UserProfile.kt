package com.lifesafety.driversafety.auth

/** The two roles. wireName is the value stored in Firestore and sent to Cloud Functions. */
enum class UserRole(val wireName: String) {
    ADMIN("admin"),
    DRIVER("driver");

    companion object {
        fun fromWire(value: String?): UserRole? = entries.firstOrNull { it.wireName == value }
    }
}

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String?,
    val role: UserRole
)
