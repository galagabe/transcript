import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  // No version: AGP 9 already bundles the Kotlin plugin on the classpath.
  id("org.jetbrains.kotlin.android")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

// Clinitex summary webhook: read from local.properties (git-ignored) or -P
// Gradle properties so the URL and device token are never committed.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun clinitexProp(name: String): String =
    (findProperty(name) as String?) ?: localProps.getProperty(name, "")

android {
    namespace = "fr.clinitex.transcription"
    compileSdk = 36
    defaultConfig {
        applicationId = "fr.clinitex.transcription"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.2.0-beta"
        // Instrumented tests (MigrationTest, DiarizationCalibrationTest) run with `adb shell am instrument`.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CLINITEX_SUMMARY_URL", "\"${clinitexProp("clinitex.summaryUrl")}\"")
        buildConfigField("String", "CLINITEX_DEVICE_TOKEN", "\"${clinitexProp("clinitex.deviceToken")}\"")
        // Lot 6a: shared Clinitex word list (same device token).
        buildConfigField("String", "CLINITEX_DICTIONARY_URL", "\"${clinitexProp("clinitex.dictionaryUrl")}\"")
        // Voxtral variant: n8n webhook forwarding audio tranches to Mistral (same device token).
        buildConfigField("String", "CLINITEX_TRANSCRIPTION_URL", "\"${clinitexProp("clinitex.transcriptionUrl")}\"")
    }

    // Lot 5: Clinitex release key. Keystore and passwords come from local.properties
    // or -P (clinitex.release.*), never from the repo. Without them, release falls
    // back to the debug key so a fresh checkout (CI) still builds.
    val releaseStoreFile = clinitexProp("clinitex.release.storeFile")
    signingConfigs {
        if (releaseStoreFile.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = clinitexProp("clinitex.release.storePassword")
                keyAlias = clinitexProp("clinitex.release.keyAlias")
                keyPassword = clinitexProp("clinitex.release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    lint {
        // False positive on ComponentActivity with this toolchain (SDK XML v4 tooling mismatch).
        disable += "Instantiatable"
    }
}

kotlin {
    jvmToolchain(17)
}

// Room schema JSON per version (exact CREATE statements used by migrations).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockwebserver)

  // Instrumented tests: Room migration tests
  androidTestImplementation(libs.room.testing)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room Database
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  // Networking (Clinitex summaries via the n8n webhook, model downloads)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)

  // Deferred Clinitex summaries: queued until the phone is back online
  implementation(libs.androidx.work.runtime.ktx)

  // tar.bz2 extraction for models distributed as archives (Parakeet 110M)
  implementation(libs.commons.compress)
}
