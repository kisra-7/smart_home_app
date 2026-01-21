import java.util.Properties
<<<<<<< HEAD
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
=======
>>>>>>> cc30e20 (fixed gradle problems)

plugins {
    id("com.android.application")
    id("kotlin-android")
<<<<<<< HEAD
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

=======
    id("dev.flutter.flutter-gradle-plugin")
}

>>>>>>> cc30e20 (fixed gradle problems)
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

<<<<<<< HEAD
    // ✅ New Kotlin compiler DSL (replaces deprecated kotlinOptions.jvmTarget)
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
=======
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
>>>>>>> cc30e20 (fixed gradle problems)
    }

    defaultConfig {
        applicationId = "com.example.alrawi_app"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

<<<<<<< HEAD
        // Expose keys to Kotlin via BuildConfig
=======
        // 🔐 Load Tuya keys from local.properties
        val props = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            props.load(localPropertiesFile.inputStream())
        }

        val tuyaAppKey = props.getProperty("tuya.appKey") ?: ""
        val tuyaAppSecret = props.getProperty("tuya.appSecret") ?: ""

>>>>>>> cc30e20 (fixed gradle problems)
        buildConfigField("String", "TUYA_APP_KEY", "\"$tuyaAppKey\"")
        buildConfigField("String", "TUYA_APP_SECRET", "\"$tuyaAppSecret\"")
    }

    buildTypes {
<<<<<<< HEAD
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
=======
        release {
            signingConfig = signingConfigs.getByName("debug")
        
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}



dependencies {
    // Load *.aar from android/app/libs (if you have any)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // Tuya core SDK
    implementation("com.thingclips.smart:thingsmart:6.11.1") {
        // if this snapshot ever appears in graph, block it
        exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
    }

    implementation("com.alibaba:fastjson:1.1.67.android")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:3.14.9")
    implementation("com.facebook.soloader:soloader:0.10.4")

    // If you're using BizBundle activator, keep it here (only if you really need it):
    // implementation("com.thingclips.smart:thingsmart-bizbundle-lamp_device_activator:2.8.0-bizbundle-2.8.0.7")
}

/**
 * ✅ Fix missing deps:
 * - Flexbox correct coordinates are com.google.android.flexbox:flexbox
 * - recyclerview-animators can be forced to a newer available version
 * ✅ Also block the broken snapshot dependency everywhere
 */
configurations.configureEach {
    // Block snapshot module that often fails to resolve
    exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")

    resolutionStrategy.eachDependency {
        // ✅ FIX: correct Flexbox group/name
        if (requested.group == "com.google.android.flexbox" && requested.name == "flexbox") {
            useVersion("3.0.0")
            because("Tuya requests older flexbox; force to an available version with correct coordinates.")
        }

        if (requested.group == "jp.wasabeef" && requested.name == "recyclerview-animators") {
            useVersion("4.0.2")
            because("Tuya requests recyclerview-animators:3.0.0; force to a newer version.")
>>>>>>> cc30e20 (fixed gradle problems)
        }
    }
}

flutter {
    source = "../.."
}
<<<<<<< HEAD

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
=======
>>>>>>> cc30e20 (fixed gradle problems)
