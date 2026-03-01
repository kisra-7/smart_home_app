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
        targetSdk = 35

        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            // Tuya BizBundle requests a non-existent artifact: com.google.android:flexbox:1.1.1
            substitute(module("com.google.android:flexbox:1.1.1"))
                .using(module("com.google.android.flexbox:flexbox:3.0.0"))

            // Tuya BizBundle requests an old version that may not resolve in your repos
            substitute(module("jp.wasabeef:recyclerview-animators:3.0.0"))
                .using(module("jp.wasabeef:recyclerview-animators:4.0.2"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    // Keep consistent: disable splits => package all ABIs (useful for emulator + real devices)
    splits {
        abi { isEnable = false }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // Only keep flatDir if you truly need local AARs in android/app/libs
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

dependencies {
    // Local AAR (only if needed)
    implementation(files("libs/security-algorithm-1.0.0-beta.aar"))

    // Tuya requirement for BizBundle compatibility (per Tuya doc in some solutions)
    implementation("org.apache.ant:ant:1.10.5")
    
    implementation("com.thingclips.smart:thingsmart-bizbundle-family")

    // ✅ Keep versions CONSISTENT (same for SDK + BizBundlesBom)
    val tuyaVersion = "6.11.6"

    // Home SDK
    implementation("com.thingclips.smart:thingsmart:$tuyaVersion")

    // BizBundle BOM (pins internal module versions)
    implementation(enforcedPlatform("com.thingclips.smart:thingsmart-BizBundlesBom:$tuyaVersion"))

    // BizBundle framework runtime
    implementation("com.thingclips.smart:thingsmart-bizbundle-basekit")
    implementation("com.thingclips.smart:thingsmart-bizbundle-bizkit")

    // Device pairing UI BizBundle
    implementation("com.thingclips.smart:thingsmart-bizbundle-device_activator")

    // ✅ QR scan BizBundle (required for ScanManager per official doc)
    implementation("com.thingclips.smart:thingsmart-bizbundle-qrcode_mlkit")
}

flutter {
    source = "../.."
}
