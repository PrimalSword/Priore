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
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.19.0")
    implementation("androidx.activity:activity:1.13.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
