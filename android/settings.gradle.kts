pluginManagement {
    val flutterSdkPath = run {
        val properties = java.util.Properties()
        val localProps = file("local.properties")
        if (!localProps.exists()) {
            error("android/local.properties not found")
        }
        localProps.inputStream().use { properties.load(it) }
        properties.getProperty("flutter.sdk")
            ?: error("flutter.sdk not set in android/local.properties")
    }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}


dependencyResolutionManagement {
    // ✅ Allow dependencies/plugins that declare their own repositories
    repositoriesMode.set(
        org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_PROJECT
    )

    repositories {
    google()
    mavenCentral()

    // ✅ Legacy fallback (needed by some Tuya BizBundle transitive deps)
    jcenter()

    maven { url = uri("https://storage.googleapis.com/download.flutter.io") }

    // Tuya / ThingClips repos
    maven { url = uri("https://maven-other.tuya.com/repository/maven-releases/") }
    maven { url = uri("https://maven-other.tuya.com/repository/maven-commercial-releases/") }

    maven { url = uri("https://jitpack.io") }
}

}


rootProject.name = "alrawi_app"
include(":app")
