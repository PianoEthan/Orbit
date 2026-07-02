plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.qx.orbit.bili"
    compileSdk {
        version = release(36){
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.qx.orbit.bili"
        minSdk = 23
        targetSdk = 36
        versionCode = 403
        versionName = "0.4.3-Alpha"
        resValue("string", "app_verCode", versionCode.toString())
        resValue("string", "app_version", versionName.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/rules.keep"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
        buildConfig = true
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86")
            isUniversalApk = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val ver = output.versionName.get()
            val abi = output.filters
                .filter { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                .map { it.identifier }
                .singleOrNull() ?: "universal"
            output.outputFileName.set("Orbit-${ver}-${abi}-release.apk")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.material3)
    implementation(libs.compose.navigation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.material.icons.extended)
    implementation(libs.core.splashscreen)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)

    // Retrofit + OkHttp + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.coroutines.core)
    implementation(libs.lifecycle.viewmodel.compose)

    // ijkplayer + DanmakuFlameMaster + brotlij
    implementation(project(":ijkplayer-java"))
    implementation(project(":DanmakuFlameMaster"))

    // Brotli decompression
    implementation(libs.dec)

    // Protobuf (for danmaku parsing)
    implementation(libs.protobuf.javalite)

    // Jsoup (for HTML parsing in OpusApi, CookieRefreshApi)
    implementation(libs.jsoup)

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Coil for Compose
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // AppCompat + RecyclerView
    implementation(libs.appcompat)
    implementation(libs.recyclerview)

    // ZXing (for QR code in LoginApi)
    implementation(libs.core)

    // PhotoView (for image viewer)
    implementation(libs.photoview)

    // EventBus
    implementation(libs.eventbus)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
