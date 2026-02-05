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

    // ✅ Dependency substitutions (keep as you had)
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
android {
    // ... keep your existing config

    splits {
        abi {
            isEnable = false
        }
    }
}

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // If you use local .aar (security-algorithm.aar) put it in android/app/libs
    repositories {
        flatDir {
            dirs("libs")
        }
    }

    // ✅ AGP 8.x packaging DSL (replaces packagingOptions)
    packaging {
        jniLibs {
            // same as your pickFirst("lib/*/libc++_shared.so")
            pickFirsts += setOf("lib/*/libc++_shared.so")
        }
       resources {
    // ✅ strongest fix for duplicate Java resources
    pickFirsts += setOf(
        "META-INF/INDEX.LIST"
    )

    // keep these too (harmless + useful)
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
    // Core Home SDK
    implementation("com.thingclips.smart:thingsmart:6.11.6")

    // UI BizBundle platform (keep versions consistent with SDK as per Tuya docs)
    implementation(enforcedPlatform("com.thingclips.smart:thingsmart-BizBundlesBom:6.11.6"))

    // Device Pairing UI BizBundle (SmartLife-like Add Device flow)
    implementation("com.thingclips.smart:thingsmart-bizbundle-device_activator")

    // QR Code scanning BizBundle (Tuya scan page)
    implementation("com.thingclips.smart:thingsmart-bizbundle-qrcode_mlkit")

    // If Tuya asked you to add security-algorithm.aar:
    // put it at android/app/libs/security-algorithm.aar then enable:
    // implementation(files("libs/security-algorithm.aar"))
}

flutter {
    source = "../.."
}
