plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "de.robv.android.xposed.stub"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
