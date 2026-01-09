pluginManagement {
    // ✅ Needed so Gradle can resolve com.android.application (AGP) + Kotlin plugins
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // ✅ REQUIRED for io.flutter:* artifacts
        maven(url = "https://storage.googleapis.com/download.flutter.io")

        // ✅ Tuya Maven repos
        maven(url = "https://maven-other.tuya.com/repository/maven-releases/")
        maven(url = "https://maven-other.tuya.com/repository/maven-commercial-releases/")
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
}

include(":app")
