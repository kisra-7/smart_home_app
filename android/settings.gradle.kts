<<<<<<< HEAD
pluginManagement {
    // ✅ Needed so Gradle can resolve com.android.application (AGP) + Kotlin plugins
=======
import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
>>>>>>> cc30e20 (fixed gradle problems)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
<<<<<<< HEAD
    }

    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }
=======
        maven(url = "https://storage.googleapis.com/download.flutter.io")
    }

    val flutterSdkPath = run {
        val properties = java.util.Properties()
        file("local.properties").inputStream().use { properties.load(it) }
        val path = properties.getProperty("flutter.sdk")
        require(path != null) { "flutter.sdk not set in local.properties" }
        path
    }
>>>>>>> cc30e20 (fixed gradle problems)

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")
}

dependencyResolutionManagement {
<<<<<<< HEAD
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // ✅ REQUIRED for io.flutter:* artifacts
        maven(url = "https://storage.googleapis.com/download.flutter.io")

        // ✅ Tuya Maven repos
        maven(url = "https://maven-other.tuya.com/repository/maven-releases/")
        maven(url = "https://maven-other.tuya.com/repository/maven-commercial-releases/")
=======
    // ✅ Works on your Gradle version (unlike ALLOW_PROJECT_REPOS)
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        google()
        mavenCentral()
        maven(url = "https://storage.googleapis.com/download.flutter.io")

        // Tuya repos
        maven(url = "https://maven-other.tuya.com/repository/maven-releases/")
        maven(url = "https://maven-other.tuya.com/repository/maven-commercial-releases/")
        maven(url = "https://maven-other.tuya.com/repository/maven-snapshots/")
        maven(url = "https://maven-other.tuya.com/repository/maven-commercial-snapshots/")

        // Huawei repo
        maven(url = "https://developer.huawei.com/repo/")

        // Optional
        maven(url = "https://jitpack.io")
>>>>>>> cc30e20 (fixed gradle problems)
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
<<<<<<< HEAD
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
=======
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
>>>>>>> cc30e20 (fixed gradle problems)
}

include(":app")
