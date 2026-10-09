plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.gaiser147.claudeauto.hook"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.gaiser147.claudeauto.hook"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Nur zum Kompilieren: die echten Klassen stellt LSPosed zur Laufzeit bereit.
    compileOnly(project(":xposedapi"))
}
