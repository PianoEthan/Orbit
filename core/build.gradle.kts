plugins {
    alias(libs.plugins.android.library)
    id("kotlin-parcelize")
}

android {
    namespace = "com.qx.orbit.bili.core"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.coroutines.core)
    implementation(libs.dec)
    implementation(libs.protobuf.javalite)
    implementation(libs.jsoup)
    implementation(libs.core)
    testImplementation("junit:junit:4.13.2")
}
