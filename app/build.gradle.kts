plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.gaiser147.claudeauto"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.gaiser147.claudeauto"
        // Android 10+. Die Spracheingabe über das Automikrofon (Phase 2) braucht Android 13+
        // und wird zur Laufzeit geprüft.
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Private Nutzung: Release mit dem Debug-Key signieren, damit es ohne Keystore installierbar ist.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
