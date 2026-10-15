plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.haoyang.qdblocker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.haoyang.qdblocker"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
