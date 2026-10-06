import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

// Read once; also used by the Worker config (documentación §7.3).
val localProps =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun quoted(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.likkapet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.likkapet"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.5"

        // Worker address and shared token (documentación §7.3). They come from local.properties and are
        // never in the repo; an empty value (a fresh clone) just means the app uses its local roasts.
        buildConfigField("String", "LIKKA_WORKER_URL", quoted(localProps.getProperty("LIKKA_WORKER_URL", "")))
        buildConfigField("String", "LIKKA_APP_TOKEN", quoted(localProps.getProperty("LIKKA_APP_TOKEN", "")))
    }

    // Documentación §13.1: the release signingConfig only exists when a keystore is configured,
    // so teammates without it can still build debug.
    val hasReleaseKeystore = localProps.getProperty("RELEASE_STORE_FILE") != null
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(localProps.getProperty("RELEASE_STORE_FILE"))
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
            // Without a keystore, assembleRelease still runs but produces an unsigned APK.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
