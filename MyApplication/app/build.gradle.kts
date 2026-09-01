plugins {
    alias(libs.plugins.android.application)
}

val buildNative = providers.gradleProperty("buildNative")
    .map { it.toBoolean() }
    .orElse(true)
    .get()

android {
    namespace = "com.example.myapplication"
    compileSdk = 37
    ndkVersion = "21.4.7075529"

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 21
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (buildNative) {
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    if (buildNative) {
        externalNativeBuild {
            cmake {
                path = file("CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation(files("libs/opencv345-debug.aar"))
}
