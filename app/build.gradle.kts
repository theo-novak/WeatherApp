// App module build config — the knobs you might touch live here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)   // required for Compose under Kotlin 2.x
}

android {
    namespace = "com.theonovak.simpleweather"
    compileSdk = 36                       // Android 16 — matches your phone

    defaultConfig {
        applicationId = "com.theonovak.simpleweather"
        minSdk = 24                       // runs on Android 7.0 and up
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false       // keep v1 simple: no code shrinking
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose = Android's modern UI toolkit. The BOM pins compatible versions.
    // NOTE: `platform(...)` is what makes the BOM apply its version pinning.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)   // async fetching without threads
}