// Android app module: RETINA Results viewer (Jetpack Compose UI in MainActivity.kt).
// APK output name is set below so sideloaded builds are easy to spot on device.

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun quotedBuildConfigString(name: String): String {
    val value = (findProperty(name) as? String) ?: localProperties.getProperty(name).orEmpty()
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.example.picoviewer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.picoviewer"
        minSdk = 26 // Required for Pico Neo 3 (Android 10)
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // HTTPS endpoint that accepts JSON from [ResultsUploader] (e.g. Google Apps Script Web App).
        // Values come from ignored local.properties or Gradle -P args so secrets are not committed.
        buildConfigField("String", "UPLOAD_ENDPOINT_URL", quotedBuildConfigString("UPLOAD_ENDPOINT_URL"))
        buildConfigField("String", "UPLOAD_SHARED_SECRET", quotedBuildConfigString("UPLOAD_SHARED_SECRET"))
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "RETINA_Results-${variant.name}.apk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
