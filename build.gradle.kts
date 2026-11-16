// Kotlin itself comes from the built-in Kotlin support of AGP 9; the Compose
// compiler plugin below pins the Kotlin Gradle plugin version it runs with.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
