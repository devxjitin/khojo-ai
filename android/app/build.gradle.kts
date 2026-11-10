import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("io.objectbox")
}

// Build-time values come from local.properties (gitignored). Nothing secret is compiled into the APK:
// anything inside an APK can be pulled out of it.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}
val apiBaseUrl: String = localProperties.getProperty("API_BASE_URL", "https://api.example.com")
// Shared key the app sends to the update endpoint. Not a real secret (it is inside the APK); Firebase JWT + activation keys replace it in Sprint 4B.
val betaKey: String = localProperties.getProperty("BETA_KEY", "")

// versionCode = minutes since 1970, so every build is newer than the one before.
// scripts/publish-update.ps1 passes the same number with -PappVersionCode.
val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toIntOrNull()
    ?: (System.currentTimeMillis() / 60000).toInt()
// versionName = newest "## x.y.z" heading in src/main/assets/changelog.md, so the app, About page and update notes agree.
val appVersionName: String = (project.findProperty("appVersionName") as String?)
    ?: file("src/main/assets/changelog.md").takeIf { it.exists() }?.useLines { lines ->
        lines.firstNotNullOfOrNull { Regex("""^##\s+v?(\d+\.\d+\.\d+)""").find(it)?.groupValues?.get(1) }
    }
    ?: "0.0.1"

// Release signing key: ~/.khojo-android/ota.keystore locally, restored from a GitHub secret in CI. There is no Play App
// Signing to fall back on, so losing or replacing it means every user must uninstall and reinstall. Back it up offline
// in two places and never regenerate it.
val otaKeystore = File(System.getProperty("user.home"), ".khojo-android/ota.keystore")
val otaStorePassword: String = System.getenv("KHOJO_OTA_KEYSTORE_PASSWORD") ?: "android"
val otaKeyAlias: String = System.getenv("KHOJO_OTA_KEY_ALIAS") ?: "androiddebugkey"
val otaKeyPassword: String = System.getenv("KHOJO_OTA_KEY_PASSWORD") ?: otaStorePassword

android {
    namespace = "com.khojoai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.khojoai.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "BETA_KEY", "\"$betaKey\"")
    }

    signingConfigs {
        if (otaKeystore.exists()) {
            create("ota") {
                storeFile = otaKeystore
                storePassword = otaStorePassword
                keyAlias = otaKeyAlias
                keyPassword = otaKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the key file (CI checks, fresh clone) the build falls back to the debug key.
            signingConfigs.findByName("ota")?.let { signingConfig = it }
        }
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

kapt { correctErrorTypes = true }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    // Themes (Theme.Material3.*) used by the manifest live in the View-based Material artifact.
    implementation("com.google.android.material:material:1.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Baseline profiles are installed on the phone; without Play nothing else compiles them ahead of time.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation("com.google.dagger:hilt-android:2.58")
    kapt("com.google.dagger:hilt-android-compiler:2.58")

    // Self-update from our AWS bucket.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // On-device vector database. Its native libraries for the computer are needed so database tests can run in CI.
    testImplementation("io.objectbox:objectbox-linux:5.4.2")
    testImplementation("io.objectbox:objectbox-windows:5.4.2")
    testImplementation("io.objectbox:objectbox-macos:5.4.2")
    testImplementation("junit:junit:4.13.2")
    // Real org.json: the Android one is only a stub in unit tests.
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
