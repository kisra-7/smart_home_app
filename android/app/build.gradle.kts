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

    // ✅ Dependency substitutions
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

    // ✅ Disable ABI splits (keep single universal debug apk)
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

    // ✅ Local AARs in android/app/libs
    repositories {
        flatDir { dirs("libs") }
    }

    // ✅ AGP 8.x packaging DSL
    packaging {
        jniLibs {
            pickFirsts += setOf("lib/*/libc++_shared.so")
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
 * Tuya / ThingClips excludes to avoid SNAPSHOT annotation modules
 */
configurations.all {
    exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
    exclude(group = "com.thingclips.smart", module = "thingplugin-annotation")
    exclude(group = "com.thingclips.android.module", module = "thingmodule-annotation")
}

dependencies {
    // ✅ Tuya security AAR (must exist at android/app/libs/security-algorithm-1.0.0-beta.aar)
    implementation(files("libs/security-algorithm-1.0.0-beta.aar"))

    implementation("com.thingclips.smart:thingsmart:6.11.6")
    implementation(enforcedPlatform("com.thingclips.smart:thingsmart-BizBundlesBom:6.11.6"))
    implementation("com.thingclips.smart:thingsmart-bizbundle-device_activator")
    implementation("com.thingclips.smart:thingsmart-bizbundle-qrcode_mlkit")
}

flutter {
    source = "../.."
}
