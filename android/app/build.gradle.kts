plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.alrawi_app"
    compileSdk = flutter.compileSdkVersion

    defaultConfig {
        applicationId = "com.example.alrawi_app"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

/**
 * Tuya / ThingClips excludes to avoid SNAPSHOT annotation modules
 */
configurations.all {
    exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
    exclude(group = "com.thingclips.smart", module = "thingplugin-annotation")
    exclude(group = "com.thingclips.android.module", module = "thingmodule-annotation")
}

dependencies {
    implementation("com.thingclips.smart:thingsmart:6.11.6")
}

flutter {
    source = "../.."
}
