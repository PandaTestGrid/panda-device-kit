plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.panda"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.panda"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

