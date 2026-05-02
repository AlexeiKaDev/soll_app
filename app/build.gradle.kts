import org.gradle.api.tasks.bundling.Zip
import java.io.InputStream
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Sherpa Android jni is built against ONNX Runtime from their scripts (see k2-fsa sherpa-onnx
// build-android-*.sh: onnxruntime_version=1.24.3). The Maven AAR must use the *same* native
// libonnxruntime.so as Sherpa; we strip only Microsoft's duplicate .so from the dependency AAR.
private val onnxRuntimeAndroidVersion = "1.24.3"

private val onnxRuntimeAndroidBase: Configuration =
    configurations.create("onnxRuntimeAndroidBase").apply {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

private val stripMicrosoftOnnxRuntimeSo by tasks.registering(Zip::class) {
    group = "prepare"
    description =
        "Rebuild onnxruntime-android AAR without jni/*/libonnxruntime.so (use Sherpa's copy only)"
    archiveFileName.set("onnxruntime-android-no-libonnxruntime-so.aar")
    destinationDirectory.set(layout.buildDirectory.dir("stripped-onnx-android"))
    dependsOn(onnxRuntimeAndroidBase)
    from(zipTree(onnxRuntimeAndroidBase.singleFile)) {
        exclude("jni/**/libonnxruntime.so")
    }
}

private val strippedOnnxAndroidAar =
    objects.fileCollection().from(stripMicrosoftOnnxRuntimeSo.flatMap { it.archiveFile })

private val downloadNatashaAsset by tasks.registering {
    group = "prepare"
    description = "Download Natasha ONNX model into app assets"
    val assetsDir = layout.projectDirectory.dir("src/main/assets/natasha_vits2")
    val modelFile = assetsDir.file("model.onnx")
    val tempFile = assetsDir.file("model.onnx.tmp")
    outputs.file(modelFile)
    doLast {
        val assetsDirFile = assetsDir.asFile
        val modelFileFile = modelFile.asFile
        val tempFileFile = tempFile.asFile
        if (!assetsDirFile.exists()) assetsDirFile.mkdirs()
        val minBytes = 80L * 1024L * 1024L
        if (modelFileFile.exists() && modelFileFile.length() >= minBytes) {
            logger.lifecycle("Natasha asset already present: ${modelFileFile.absolutePath}")
            return@doLast
        }
        val url = URL(
            "https://huggingface.co/frappuccino/vits2_ru_natasha/resolve/main/natasha.onnx",
        )
        logger.lifecycle("Downloading Natasha model to assets (one-time)...")
        url.openStream().use { input: InputStream ->
            tempFileFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (tempFileFile.length() < minBytes) {
            val actual = tempFileFile.length()
            tempFileFile.delete()
            error("Downloaded Natasha model is too small: $actual bytes")
        }
        if (modelFileFile.exists()) modelFileFile.delete()
        tempFileFile.renameTo(modelFileFile)
        logger.lifecycle("Natasha model saved: ${modelFileFile.absolutePath} (${modelFileFile.length()} bytes)")
    }
}

android {
    namespace = "com.soll"
    compileSdk = 34

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
        }
        release {
            isMinifyEnabled = true
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
}

tasks.named("preBuild") {
    dependsOn(downloadNatashaAsset)
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

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Background
    implementation(libs.work.runtime.ktx)

    // Media session for TTS controls
    implementation("androidx.media:media:1.7.0")

    // Utrobin: Java onnxruntime API + JNI from stripped AAR; libonnxruntime.so comes from Sherpa AAR.
    implementation(strippedOnnxAndroidAar)
    implementation(files("libs/sherpa-onnx.aar"))

    // Logging
    implementation(libs.timber)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Camera
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Google Play Services
    implementation(libs.play.services.location)

    // EPUB parsing
    implementation(libs.jsoup)
}

