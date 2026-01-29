import org.gradle.api.tasks.Delete

// IMPORTANT: Make Android outputs go to the Flutter root /build folder
buildDir = file("../build")

subprojects {
    buildDir = file("${rootProject.buildDir}/${project.name}")
}

subprojects {
    evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://storage.googleapis.com/download.flutter.io")
    }
}
