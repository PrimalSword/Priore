plugins {
    id("com.android.application")
}

android {
    namespace = "com.primalsword.priore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.primalsword.priore"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.activity:activity:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
