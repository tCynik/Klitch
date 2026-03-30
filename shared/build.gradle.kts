plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }

    // iOS-таргеты — раскомментировать при добавлении iOS-платформы
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    jvm() // для unit-тестов на JVM без эмулятора

    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation(libs.coroutines.core)

            // Serialization
            implementation(libs.serialization.json)

            // Утилиты
            implementation(libs.datetime)
            implementation(libs.immutable.collections)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)

            // Koin
            implementation(libs.koin.core)

            // SQLDelight
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            // Settings / Preferences
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)

            // ViewModel (KMP)
            implementation(libs.lifecycle.viewmodel)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.coroutines.android)
            implementation(libs.koin.android)
        }

        // iosMain.dependencies {
        //     implementation(libs.ktor.client.darwin)
        //     implementation(libs.sqldelight.native.driver)
        // }

        jvmMain.dependencies {
            implementation(libs.sqldelight.jvm.driver)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.kotest.assertions)
            implementation(libs.ktor.client.mock)
        }

        androidUnitTest.dependencies {
            implementation(libs.mockk)
        }
    }
}

android {
    namespace = "ru.tcynik.mymesh1.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("ru.tcynik.mymesh1.data.local")
        }
    }
}
