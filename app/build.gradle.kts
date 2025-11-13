plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.perseitech.sailpilot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.perseitech.sailpilot"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Se usi FusedLocation, serve questa dichiarazione di feature opzionale
        // manifestPlaceholders["usesCleartextTraffic"] = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // più facile da fare debug
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    // Evita conflitti di risorse (necessario con JTS/altre lib)
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/**",
                "META-INF/*.kotlin_module",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }
}

dependencies {
    // Kotlin/Android base
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    // WebSocket Signal K
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.squareup.okhttp3:okhttp:4.11.0")


    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Material3 (se usi M3)
    implementation("androidx.compose.material3:material3:1.3.1")

    // Se usi ancora Material 2, tieni quello. In ogni caso, per le icone:
    implementation ("androidx.compose.material:material-icons-extended:1.7.5")

    // Compose BOM + UI
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("com.google.android.material:material:1.12.0")


    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // OSMdroid (mappe)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // JTS (geometrie / WKT)
    implementation("org.locationtech.jts:jts-core:1.19.0")

    // (Opzionale) Fused Location Provider, se lo usi nel tuo LocationService
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
