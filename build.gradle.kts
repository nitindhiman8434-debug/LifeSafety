// Root build file. Plugin versions live in gradle/libs.versions.toml.
// Plugins are declared here with "apply false" and applied inside app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
}
