plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.alrawi_app"

    // Keep Flutter compileSdk (your emulator is API 36 anyway)
    compileSdk = flutter.compileSdkVersion

    defaultConfig {
        applicationId = "com.example.alrawi_app"

        // ✅ REQUIRED by Tuya UI BizBundle framework
        minSdk = flutter.minSdkVersion
        targetSdk = 35

        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.google.android:flexbox:1.1.1"))
                .using(module("com.google.android.flexbox:flexbox:3.0.0"))

            substitute(module("jp.wasabeef:recyclerview-animators:3.0.0"))
                .using(module("jp.wasabeef:recyclerview-animators:4.0.2"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    splits {
        abi {
            isEnable = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    repositories {
        flatDir { dirs("libs") }
    }

    packaging {
        jniLibs {
            pickFirsts += setOf(
                "lib/*/liblog.so",
                "lib/*/libc++_shared.so",
                "lib/*/libyuv.so",
                "lib/*/libopenh264.so",
                "lib/*/libv8wrapper.so",
                "lib/*/libv8android.so"
            )
        }
        resources {
            pickFirsts += setOf("META-INF/INDEX.LIST")
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

/**
 * ✅ IMPORTANT:
 * Do NOT exclude Thing/Tuya annotation/plugin artifacts globally.
 * It can break BizBundle/Activator runtime class loading.
 */

dependencies {
    // Tuya docs commonly require this for BizBundle compatibility
    implementation("org.apache.ant:ant:1.10.5")

    // (Optional) Your local security AAR (keep only if you actually need it)
    implementation(files("libs/security-algorithm-1.0.0-beta.aar"))

    // ✅ Core Home SDK
    implementation("com.thingclips.smart:thingsmart:6.11.6")

    // ✅ UI BizBundle BOM (pins correct versions)
    implementation(enforcedPlatform("com.thingclips.smart:thingsmart-BizBundlesBom:6.11.6"))

    // ✅ UI BizBundle framework runtime
    implementation("com.thingclips.smart:thingsmart-bizbundle-basekit")
    implementation("com.thingclips.smart:thingsmart-bizbundle-bizkit")

    // ✅ Device pairing UI BizBundle
    implementation("com.thingclips.smart:thingsmart-bizbundle-device_activator")
    implementation("com.thingclips.smart:thingsmart-bizbundle-qrcode_mlkit")

    // ✅ Home/Family
    implementation("com.thingclips.smart:thingsmart-bizbundle-family")
}

flutter {
    source = "../.."
}
