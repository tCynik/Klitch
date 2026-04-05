plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.tcynik.meshtactics"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.tcynik.meshtactics"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":mesh"))

    // Multiplatform Settings — required for LastMapPositionRepositoryImpl in app/data/local
    implementation(libs.multiplatform.settings)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // Lifecycle + ViewModel (JetBrains KMP-совместимые)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    // Process lifecycle — required for mesh layer BLE/network Koin bindings
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")

    // Navigation (JetBrains KMP-совместимый)
    implementation(libs.navigation.compose)

    // Immutable collections
    implementation(libs.immutable.collections)

    // Coroutines
    implementation(libs.coroutines.android)

    // WorkManager
    implementation(libs.work.runtime)

    // Koin — DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    // maplibre-compose — Jetpack Compose wrapper for MapLibre (pulls in android-sdk transitively)
    // Note: if field device OpenGL ES compatibility issues arise, investigate android-sdk-opengl variant
    implementation("org.maplibre.compose:maplibre-compose:0.12.1")

    // Тестирование
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
