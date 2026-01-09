import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("kotlin-android")
    // Flutter Gradle Plugin must be applied after Android/Kotlin plugins
    id("dev.flutter.flutter-gradle-plugin")
}

/**
 * Load values from android/local.properties (rootProject file)
 * We keep secrets OUT of git.
 *
 * Expected keys in local.properties:
 * tuya.appKey=xxxx
 * tuya.appSecret=yyyy
 */
val localProps = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProps.load(it) }
}

val tuyaAppKey: String = localProps.getProperty("tuya.appKey") ?: ""
val tuyaAppSecret: String = localProps.getProperty("tuya.appSecret") ?: ""

android {
    namespace = "com.example.alrawi_app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ✅ New Kotlin compiler DSL (replaces deprecated kotlinOptions.jvmTarget)
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    defaultConfig {
        applicationId = "com.example.alrawi_app"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // Expose keys to Kotlin via BuildConfig
        buildConfigField("String", "TUYA_APP_KEY", "\"$tuyaAppKey\"")
        buildConfigField("String", "TUYA_APP_SECRET", "\"$tuyaAppSecret\"")
    }

    buildTypes {
        debug {
            // In debug we allow empty keys so the app can still run,
            // but you SHOULD fill them to test Tuya features.
        }
        release {
            // ✅ Fail fast so you NEVER ship without keys
            if (tuyaAppKey.isBlank() || tuyaAppSecret.isBlank()) {
                throw GradleException(
                    "Missing Tuya keys in local.properties. Please set:\n" +
                    "tuya.appKey=...\n" +
                    "tuya.appSecret=..."
                )
            }
            signingConfig = signingConfigs.getByName("debug") // replace later with release keystore
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Load *.aar from android/app/libs (for security-algorithm.aar)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // Tuya core SDK
    implementation("com.thingclips.smart:thingsmart:6.11.1") {
        // ✅ Avoid missing SNAPSHOT dependency
        exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
    }

    // Required by Tuya
    implementation("com.alibaba:fastjson:1.1.67.android")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:3.14.9")
    implementation("com.facebook.soloader:soloader:0.10.4")
}
