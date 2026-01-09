plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "2.3.4"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

android {
    namespace = "com.android.mobilecamera"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.mobilecamera"
        minSdk = 26
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
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Базовые AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.ui)

    // ===== EXIF для правильной ориентации фото =====
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Тесты
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ===== COMPOSE =====
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Compose Activity
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // ===== NAVIGATION (только Compose) =====
    implementation("androidx.navigation:navigation-compose:2.9.6")

    // ===== CAMERAX =====
    val camerax_version = "1.5.2"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-extensions:$camerax_version")

    // ===== ROOM =====
    val room_version = "2.8.4"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // ===== COIL (загрузка изображений и видео) =====
    val coil_version = "2.7.0"
    implementation("io.coil-kt:coil-compose:$coil_version")
    implementation("io.coil-kt:coil-video:$coil_version")

    // ===== MEDIA3 (ExoPlayer для видео) =====
    val media3_version = "1.9.0"
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-ui-compose-material3:$media3_version")

    // ===== COROUTINES =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}