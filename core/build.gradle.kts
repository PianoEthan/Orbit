import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.qx.orbit.bili.core"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 23
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        withHostTestBuilder {}
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMain.get())
            dependencies {
                api(libs.okhttp)
                implementation(libs.gson)
                implementation(libs.coroutines.core)
                implementation(libs.jsoup)
            }
        }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmMain.get().dependsOn(jvmAndAndroidMain)

        val jvmAndAndroidTest = create("jvmAndAndroidTest") {
            dependsOn(commonTest.get())
            dependencies {
                implementation(libs.junit)
            }
        }
        named("androidHostTest") { dependsOn(jvmAndAndroidTest) }
        jvmTest.get().dependsOn(jvmAndAndroidTest)
    }
}
