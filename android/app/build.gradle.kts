plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dev.flutter.flutter-gradle-plugin")
}

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

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "com.example.alrawi_app"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // 🔐 Load Tuya keys from local.properties
        val props = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            props.load(localPropertiesFile.inputStream())
        }

        val tuyaAppKey = props.getProperty("tuya.appKey") ?: ""
        val tuyaAppSecret = props.getProperty("tuya.appSecret") ?: ""

        buildConfigField("String", "TUYA_APP_KEY", "\"$tuyaAppKey\"")
        buildConfigField("String", "TUYA_APP_SECRET", "\"$tuyaAppSecret\"")
    }

    buildFeatures {
        buildConfig = true
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
        }
    }
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
