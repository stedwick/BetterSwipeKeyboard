plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional Play upload signing. Set these in ~/.gradle/gradle.properties
// (NEVER in this repo — the keystore and passwords must not be committed):
//   uploadStoreFile=/Users/philip/upload-keystore.jks
//   uploadStorePassword=...
//   uploadKeyPassword=...
// Without them the release build simply stays unsigned (local/CI use).
val uploadStoreFile = providers.gradleProperty("uploadStoreFile").orNull?.let(::file)
val uploadSigningAvailable = uploadStoreFile != null && uploadStoreFile.exists()

android {
    namespace = "com.example.betterswipekeyboard"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // The Play Store identity of the app — permanent once uploaded.
        // (namespace stays com.example.betterswipekeyboard; it's only the
        // code package and doesn't affect the store listing.)
        applicationId = "com.philpdx.keyboard"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadSigningAvailable) {
            create("release") {
                storeFile = uploadStoreFile
                storePassword = providers.gradleProperty("uploadStorePassword").get()
                keyAlias = providers.gradleProperty("uploadKeyAlias").orNull ?: "upload"
                keyPassword = providers.gradleProperty("uploadKeyPassword").get()
            }
        }
    }
    buildTypes {
        release {
            if (uploadSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        // Make the swipe dictionary asset available to local unit tests.
        getByName("test") { resources.srcDir("src/main/assets") }
    }
}

// Proofread eval harness (tools/eval/): JVM entry point living in the test
// source set, run via JavaExec against the unit-test task's classpath (test
// classes + assets + dependencies).
tasks.register<JavaExec>("generateEvalCorpus") {
    group = "verification"
    description = "Builds tools/eval/corpus.jsonl from the swipe fixtures and invented cases."
    mainClass.set("com.example.betterswipekeyboard.eval.CorpusGeneratorKt")
    val unitTest = tasks.named<Test>("testDebugUnitTest")
    classpath = files({ unitTest.get().classpath })
    workingDir = rootDir
    dependsOn("assembleDebugUnitTest")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.mlkit.genai.proofreading)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}