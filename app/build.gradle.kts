plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

android {
    namespace = "com.qx.orbit.bili"
    compileSdk {
        version = release(37){
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.qx.orbit.bili"
        minSdk = 23
        targetSdk = 36
        versionCode = 510
        versionName = "0.5.10-Alpha"
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
    flavorDimensions += "player"
    productFlavors {
        create("full") {
            dimension = "player"
            buildConfigField("boolean", "HAS_IJK", "true")
            proguardFiles("src/full/keepRules/ijk.keep")
            versionNameSuffix = "-full"
        }
        create("lite") {
            dimension = "player"
            buildConfigField("boolean", "HAS_IJK", "false")
            versionNameSuffix = "-lite"
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
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

    // ijkplayer (full flavor only) + DFMNext + DanmakuFlameMaster
    "fullImplementation"(project(":ijkplayer-java"))
    implementation(project(":DFMNext"))
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

    // Material Color Utilities (Monet)
    implementation(libs.material.color.utilities)

    // AppCompat + RecyclerView
    implementation(libs.appcompat)
    implementation(libs.recyclerview)

    // ZXing (for QR code in LoginApi)
    implementation(libs.core)

    // PhotoView (for image viewer)
    implementation(libs.photoview)

    // Horologist Audio UI
    implementation(libs.horologist.audio.ui)
    implementation(libs.horologist.media.ui)

    // Media3 (ExoPlayer)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)

    // EventBus
    implementation(libs.eventbus)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
