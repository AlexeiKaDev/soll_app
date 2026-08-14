import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

abstract class StripOnnxRuntimeAarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputAar: RegularFileProperty

    @get:OutputFile
    abstract val outputAar: RegularFileProperty

    @TaskAction
    fun strip() {
        val input = inputAar.get().asFile
        val output = outputAar.get().asFile
        output.parentFile.mkdirs()

        ZipInputStream(input.inputStream().buffered()).use { zipIn ->
            ZipOutputStream(output.outputStream().buffered()).use { zipOut ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.matches(ONNX_RUNTIME_SO_ENTRY)) {
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }

                    val outEntry = ZipEntry(name).apply {
                        time = entry.time
                        comment = entry.comment
                        extra = entry.extra
                    }
                    zipOut.putNextEntry(outEntry)
                    if (!entry.isDirectory) {
                        zipIn.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
    }

    private companion object {
        val ONNX_RUNTIME_SO_ENTRY = Regex("""jni/.*/libonnxruntime\.so""")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

private val hasGoogleServicesConfig = listOf(
    "google-services.json",
    "src/debug/google-services.json",
    "src/release/google-services.json",
).any { relativePath -> file(relativePath).exists() }

if (hasGoogleServicesConfig) {
    pluginManager.apply("com.google.gms.google-services")
}

private val onnxRuntimeAndroidVersion = "1.24.3"

private val onnxRuntimeAndroidBase: Configuration =
    configurations.create("onnxRuntimeAndroidBase").apply {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

private val onnxRuntimeAndroidAar =
    layout.file(onnxRuntimeAndroidBase.elements.map { elements -> elements.single().asFile })

private val stripMicrosoftOnnxRuntimeSo by tasks.registering(StripOnnxRuntimeAarTask::class) {
    group = "prepare"
    description =
        "Rebuild onnxruntime-android AAR without jni/*/libonnxruntime.so (use Sherpa's copy only)"
    inputAar.set(onnxRuntimeAndroidAar)
    outputAar.set(layout.buildDirectory.file("stripped-onnx-android/onnxruntime-android-no-libonnxruntime-so.aar"))
    dependsOn(onnxRuntimeAndroidBase)
}

private val strippedOnnxAndroidAar =
    objects.fileCollection().from(stripMicrosoftOnnxRuntimeSo.flatMap { it.outputAar })

private val hiltJavaProcessors = listOf(
    "dagger.hilt.processor.internal.uninstallmodules.UninstallModulesProcessor",
    "dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputProcessor",
    "dagger.hilt.android.processor.internal.viewmodel.ViewModelProcessor",
    "dagger.hilt.processor.internal.aliasof.AliasOfProcessor",
    "dagger.hilt.processor.internal.root.RootProcessor",
    "dagger.hilt.processor.internal.earlyentrypoint.EarlyEntryPointProcessor",
    "dagger.hilt.processor.internal.definecomponent.DefineComponentProcessor",
    "dagger.hilt.processor.internal.root.ComponentTreeDepsProcessor",
    "dagger.hilt.android.processor.internal.bindvalue.BindValueProcessor",
    "dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsProcessor",
    "dagger.hilt.processor.internal.originatingelement.OriginatingElementProcessor",
    "dagger.hilt.android.processor.internal.androidentrypoint.AndroidEntryPointProcessor",
    "dagger.hilt.android.processor.internal.customtestapplication.CustomTestApplicationProcessor",
    "dagger.internal.codegen.ComponentProcessor",
).joinToString(",")

android {
    namespace = "com.soll"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.soll"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        jniLibs {
            pickFirsts += "lib/**/libonnxruntime.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

tasks.withType<JavaCompile>().configureEach {
    if (name.startsWith("hiltJavaCompile")) {
        options.compilerArgs.addAll(listOf("-processor", hiltJavaProcessors))
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    add(
        onnxRuntimeAndroidBase.name,
        "com.microsoft.onnxruntime:onnxruntime-android:$onnxRuntimeAndroidVersion",
    )

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.androidx.test.runner)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Storage
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.androidx.documentfile)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Background
    implementation(libs.work.runtime.ktx)

    // Media session for TTS controls
    implementation(libs.androidx.media)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.commons.compress)

    // Java ONNX Runtime API + JNI from stripped AAR; libonnxruntime.so comes from Sherpa AAR.
    implementation(strippedOnnxAndroidAar)
    implementation(files("libs/sherpa-onnx.aar"))

    // Logging
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Camera
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // Google Play Services
    implementation(libs.play.services.location)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // EPUB parsing
    implementation(libs.jsoup)
}

