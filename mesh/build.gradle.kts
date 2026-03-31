plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.wire)
    alias(libs.plugins.koin.compiler.plugin)
}

android {
    namespace = "ru.tcynik.mymesh1.mesh"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
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
        aidl = true
    }
}

wire {
    sourcePath {
        srcDir("src/main/proto")
        srcDir("src/main/wire-includes")
    }
    kotlin {
        // Wire 6 optimization: Avoid unnecessary immutable copies of repeated/map fields.
        makeImmutableCopies = false
        // Flattens 'oneof' fields into nullable properties on the parent class.
        boxOneOfsMinSize = 5000
    }
    root("meshtastic.*")
    prune("meshtastic.MeshPacket#delayed")
    prune("meshtastic.MeshPacket.Delayed")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Wire protobuf runtime
    implementation(libs.wire.runtime)

    // Kable BLE (multiplatform BLE)
    implementation(libs.kable.core)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // AtomicFU
    implementation(libs.atomicfu)

    // Kermit logging
    implementation(libs.kermit)

    // WorkManager
    implementation(libs.work.runtime)

    // Coroutines
    implementation(libs.coroutines.android)

    // kotlinx-datetime
    implementation(libs.datetime)

    // Ktor (for ApiService)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // Serialization
    implementation(libs.serialization.json)

    // Immutable collections
    implementation(libs.kotlinx.collections.immutable)

    // Okio (used by Wire)
    implementation(libs.okio)

    // MQTT
    implementation(libs.kmqtt.client)
    implementation(libs.kmqtt.common)

    // USB Serial
    implementation(libs.usb.serial.android)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.annotations)
    implementation(libs.koin.androidx.workmanager)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.location.altitude)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
}
