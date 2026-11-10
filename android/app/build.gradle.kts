import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

// Build-time values come from local.properties (gitignored). Nothing secret is compiled into the APK:
// anything inside an APK can be pulled out of it.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}
val apiBaseUrl: String = localProperties.getProperty("API_BASE_URL", "https://api.example.com")

// versionCode = minutes since 1970, so every build is newer than the one before and `ota` / `play` flavors never collide.
// scripts/publish-update.ps1 passes the same number with -PappVersionCode.
val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toIntOrNull()
    ?: (System.currentTimeMillis() / 60000).toInt()
// versionName = newest "## x.y.z" heading in src/main/assets/changelog.md, so the app, About page and update notes agree.
val appVersionName: String = (project.findProperty("appVersionName") as String?)
    ?: file("src/main/assets/changelog.md").takeIf { it.exists() }?.useLines { lines ->
        lines.firstNotNullOfOrNull { Regex("""^##\s+v?(\d+\.\d+\.\d+)""").find(it)?.groupValues?.get(1) }
    }
    ?: "0.0.1"

// OTA signing key: ~/.khojo-android/ota.keystore locally, restored from a GitHub secret in CI. Losing or replacing
// it means every tester must uninstall and reinstall, so back it up offline and never regenerate it.
val otaKeystore = File(System.getProperty("user.home"), ".khojo-android/ota.keystore")
val otaStorePassword: String = System.getenv("KHOJO_OTA_KEYSTORE_PASSWORD") ?: "android"
val otaKeyAlias: String = System.getenv("KHOJO_OTA_KEY_ALIAS") ?: "androiddebugkey"
val otaKeyPassword: String = System.getenv("KHOJO_OTA_KEY_PASSWORD") ?: otaStorePassword

val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE", "")
val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
val hasPlaySigning = releaseStoreFile.isNotBlank() && releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() && releaseKeyPassword.isNotBlank()

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
    }

    // Two ways to distribute the same code:
    //  ota  - self-updates from our AWS bucket (team + pilot sellers). Contains the APK installer; has its own app id
    //         so it can live next to the Play build on one phone.
    //  play - Google Play only: Play updates + Play Billing. Contains no installer code or permission.
    flavorDimensions += "distribution"
    productFlavors {
        create("ota") {
            dimension = "distribution"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("String", "UPDATE_CHANNEL", "\"aws-ota\"")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "UPDATE_CHANNEL", "\"google-play\"")
        }
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
        if (hasPlaySigning) {
            create("playUpload") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Pick each flavor's signing key (variants without a configured key fall back to the debug key so CI can still build).
    productFlavors.configureEach {
        when (name) {
            "ota" -> signingConfigs.findByName("ota")?.let { signingConfig = it }
            "play" -> signingConfigs.findByName("playUpload")?.let { signingConfig = it }
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

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
