import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Set by CI
val buildNumber = (findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
val buildTag = "android.$buildNumber"

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.min.assetstudio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.min.assetstudio"
        minSdk = 26
        // targetSdk 28: 29+ cannot exec files from data dir
        targetSdk = 28
        versionCode = buildNumber
        versionName = buildTag
        buildConfigField("String", "BUILD_TAG", "\"$buildTag\"")
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "zip" }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi", "ScopedStorage")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
