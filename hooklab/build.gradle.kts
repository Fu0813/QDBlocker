plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.haoyang.qdblocker.lab"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.haoyang.qdblocker.lab"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
