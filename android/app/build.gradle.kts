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
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

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

/**
 * Force-copy the APK to the location flutter_tools expects:
 * <projectRoot>/build/app/outputs/flutter-apk/app-debug.apk
 */
afterEvaluate {
    val flutterApkDir = rootProject.file("../build/app/outputs/flutter-apk")

    tasks.named("assembleDebug").configure {
        doLast {
            val apk = layout.buildDirectory
                .file("outputs/apk/debug/app-debug.apk")
                .get()
                .asFile

            if (!apk.exists()) {
                throw GradleException("APK not found at: ${apk.absolutePath}")
            }

            flutterApkDir.mkdirs()
            val dest = file("${flutterApkDir.absolutePath}/app-debug.apk")
            apk.copyTo(dest, overwrite = true)

            println("✅ Copied APK to: ${dest.absolutePath}")
        }
    }
}
