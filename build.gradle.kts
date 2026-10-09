plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 bringt Kotlin-Support mit; das Plugin wird hier nur geladen, um die Kotlin-Version festzulegen.
    alias(libs.plugins.kotlin.android) apply false
}
