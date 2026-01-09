import org.gradle.api.file.Directory
import org.gradle.api.tasks.Delete

// ✅ Keep the root build output in /build at the Flutter project root
val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()

rootProject.layout.buildDirectory.value(newBuildDir)

// ✅ Make every subproject (app, plugins, etc.) place its build outputs under that same root build folder
subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}

// ✅ Ensures :app is evaluated before others that may depend on it
subprojects {
    project.evaluationDependsOn(":app")
}

// ✅ Standard clean task (Flutter calls this too)
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
