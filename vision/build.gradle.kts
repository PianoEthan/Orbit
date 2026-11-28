import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.qx.orbit.bili.vision.shared"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 24
        androidResources.enable = true
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        withHostTestBuilder {}
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm("desktop") {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core"))
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.ui)
                implementation(libs.compose.multiplatform.resources)
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.compose.multiplatform.icons)
                implementation(libs.compose.multiplatform.backhandler)
                implementation(libs.coroutines.core)
            }
        }
        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.compose.multiplatform.navigation)
                implementation(libs.compose.multiplatform.viewmodel)
                implementation(libs.compose.multiplatform.lifecycle)
                implementation(libs.coil.multiplatform.compose)
                implementation(libs.coil.multiplatform.network)
                implementation(libs.core)
            }
        }
        androidMain {
            dependsOn(jvmAndAndroidMain)
            dependencies {
                implementation(project(":ijkplayer-java"))
                implementation(project(":DFMNext"))
                implementation(libs.activity.compose)
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.datasource.okhttp)
                implementation(libs.vision.material3)
                implementation(libs.vision.navigation)
            }
        }
        named("desktopMain") {
            dependsOn(jvmAndAndroidMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.coroutines.swing)
                implementation(libs.vlcj)
            }
        }

        val jvmAndAndroidTest = create("jvmAndAndroidTest") {
            dependsOn(commonTest.get())
            dependencies {
                implementation(libs.junit)
                implementation(libs.coroutines.test)
            }
        }
        named("androidHostTest") { dependsOn(jvmAndAndroidTest) }
        named("desktopTest") { dependsOn(jvmAndAndroidTest) }
        named("androidDeviceTest") {
            dependencies {
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation("androidx.compose.ui:ui-test-junit4")
                implementation(libs.ui.test.manifest)
                implementation("androidx.test:runner:1.7.0")
                implementation("androidx.test:rules:1.7.0")
                implementation("androidx.test.espresso:espresso-core:3.7.0")
                implementation(libs.junit)
            }
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

compose.resources {
    packageOfResClass = "com.qx.orbit.bili.vision.resources"
}

compose.desktop {
    application {
        mainClass = "com.qx.orbit.bili.vision.VisionDesktopKt"
        nativeDistributions {
            packageName = "orbit-vision"
            packageVersion = "0.1.0"
            modules("java.logging", "java.prefs", "java.xml", "jdk.crypto.ec", "jdk.unsupported")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
            )
        }
    }
}
