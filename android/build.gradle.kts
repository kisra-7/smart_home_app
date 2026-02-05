import org.gradle.api.tasks.Delete

allprojects {
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


tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
