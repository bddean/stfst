plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cx.yeet.stfst"
    compileSdk = 34

    defaultConfig {
        applicationId = "cx.yeet.stfst"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // The dictionaries are memory mapped after being copied out, and compressing
    // them in the APK only makes the copy slower.
    androidResources {
        noCompress += listOf("hfstol", "toml", "txt")
    }

    packaging {
        // Unpacked to disk at install time. Mapping it straight out of the APK
        // needs the zip aligned to the device's page size as well as the library.
        jniLibs.useLegacyPackaging = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed on purpose: this is a sideloaded prototype, and a real
            // signing key would have to live somewhere.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
