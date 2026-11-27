import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nzel.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nzel.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val b64 = System.getenv("NZEL_KEYSTORE_B64")
            if (!b64.isNullOrBlank()) {
                val ksFile = layout.buildDirectory.file("release.keystore").get().asFile
                ksFile.parentFile.mkdirs()
                ksFile.writeBytes(Base64.getDecoder().decode(b64))
                storeFile = ksFile
            } else {
                storeFile = rootProject.file("debug-fallback.keystore")
            }
            storePassword = System.getenv("NZEL_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("NZEL_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("NZEL_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("NZEL_KEYSTORE_B64").isNullOrBlank())
                signingConfigs.getByName("debug") else signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt",
                "META-INF/license.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/notice.txt",
                "META-INF/ASL2.0", "META-INF/*.kotlin_module")
        }
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.github.yausername.youtubedl-android:library:0.14.0")
    implementation("com.github.yausername.youtubedl-android:ffmpeg:0.14.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
