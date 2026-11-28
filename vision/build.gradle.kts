import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

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
                implementation(libs.jna.jpms)
                implementation(libs.gson)
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

abstract class StageVisionMpvRuntime @Inject constructor(private val files: FileSystemOperations) : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    abstract val runtimeDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val resourceRoot: DirectoryProperty

    @TaskAction
    fun stage() {
        val output = resourceRoot.get().asFile
        val input = runtimeDirectory.orNull
        if (input == null) files.delete { delete(output) }
        else files.sync {
            from(input) { into("common/mpv") }
            into(output)
        }
        output.mkdirs()
    }
}

abstract class VerifyVisionMpvRuntime : DefaultTask() {
    @get:InputDirectory
    abstract val resourceRoot: DirectoryProperty

    @get:Input
    abstract val libraryNames: ListProperty<String>

    @TaskAction
    fun verify() {
        val runtime = resourceRoot.dir("common/mpv").get().asFile
        check(libraryNames.get().any { runtime.resolve(it).isFile }) {
            "Set -Porbit.mpvRuntimeDir to the directory containing libmpv and its portable runtime dependencies."
        }
    }
}

val desktopMpvRuntimePath = providers.gradleProperty("orbit.mpvRuntimeDir")
    .orElse(providers.environmentVariable("ORBIT_MPV_RUNTIME_DIR"))
    .orNull
val stageVisionMpvRuntime = tasks.register<StageVisionMpvRuntime>("stageVisionMpvRuntime") {
    if (desktopMpvRuntimePath != null) runtimeDirectory.fileValue(rootProject.file(desktopMpvRuntimePath))
    resourceRoot.set(layout.buildDirectory.dir("desktopMpvResources"))
}
val verifyVisionMpvRuntime = tasks.register<VerifyVisionMpvRuntime>("verifyVisionMpvRuntime") {
    resourceRoot.set(stageVisionMpvRuntime.flatMap { it.resourceRoot })
    libraryNames.set(providers.systemProperty("os.name").map { name ->
        when {
            name.startsWith("Windows") -> listOf("mpv-2.dll", "libmpv-2.dll", "mpv.dll")
            name.startsWith("Mac") -> listOf("libmpv.2.dylib", "libmpv.dylib")
            else -> listOf("libmpv.so.2", "libmpv.so")
        }
    })
}
tasks.withType<AbstractJPackageTask>().configureEach { dependsOn(verifyVisionMpvRuntime) }

compose.desktop {
    application {
        mainClass = "com.qx.orbit.bili.vision.VisionDesktopKt"
        nativeDistributions {
            appResourcesRootDir.set(stageVisionMpvRuntime.flatMap { it.resourceRoot })
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

// Keep each running JVM on the JARs it started with, even when another build publishes new JARs.
abstract class SnapshotVisionRunClasspath @Inject constructor(
    private val objects: ObjectFactory,
    private val projectDirectory: File,
) : Action<Task> {
    override fun execute(task: Task) {
        val run = task as JavaExec
        val workTree = projectDirectory.toPath()
        val jars = run.classpath.files
        val snapshot = Files.createTempDirectory("orbit-vision-run-")
        val classpath = jars.mapIndexed { index, jar ->
            if (jar.extension == "jar" && jar.toPath().startsWith(workTree)) {
                Files.copy(jar.toPath(), snapshot.resolve("$index-${jar.name}")).toFile()
            } else jar
        }
        run.classpath = objects.fileCollection().from(classpath)
    }
}

class DeleteVisionRunSnapshot : Action<Task> {
    override fun execute(task: Task) {
        val temporaryDirectory = File(System.getProperty("java.io.tmpdir"))
        (task as JavaExec).classpath.files.mapNotNull { jar ->
            jar.parentFile?.takeIf {
                it.name.startsWith("orbit-vision-run-") && it.parentFile == temporaryDirectory
            }
        }.distinct().forEach { it.deleteRecursively() }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        doFirst(objects.newInstance<SnapshotVisionRunClasspath>(rootProject.layout.projectDirectory.asFile))
        doLast(DeleteVisionRunSnapshot())
    }
}
