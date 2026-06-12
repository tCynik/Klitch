import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystoreProps = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) load(propsFile.inputStream())
}

android {
    namespace = "ru.tcynik.klitch"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.tcynik.klitch"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreProps["storeFile"]?.let { file(it) }
            storePassword = keystoreProps["storePassword"] as String?
            keyAlias = keystoreProps["keyAlias"] as String?
            keyPassword = keystoreProps["keyPassword"] as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.core.splashscreen)
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

    // DataStore
    implementation(libs.datastore.preferences)
    implementation(libs.serialization.json)

    // Coroutines
    implementation(libs.coroutines.android)

    // SQLDelight — coroutines extensions (asFlow / mapToList used in ImportedMapRepositoryImpl)
    implementation(libs.sqldelight.coroutines)
    testImplementation(libs.sqldelight.jvm.driver)

    // WorkManager
    implementation(libs.work.runtime)

    // Koin — DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    // OSMBonusPack — KML/KMZ parser
    implementation("com.github.MKergall:osmbonuspack:6.9.0")

    // maplibre-compose — Jetpack Compose wrapper for MapLibre (pulls in android-sdk transitively)
    // Note: if field device OpenGL ES compatibility issues arise, investigate android-sdk-opengl variant
    implementation("org.maplibre.compose:maplibre-compose:0.12.1")

    // OkHttp — explicit declaration required for TileCacheInterceptor / TileCacheOkHttpConfigurator
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

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
