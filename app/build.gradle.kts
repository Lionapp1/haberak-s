import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun prop(name: String): String? = System.getenv(name) ?: keystoreProps.getProperty(name)

android {
    namespace = "com.lion.filebeam"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lion.filebeam"
        minSdk = 24
        targetSdk = 34
        versionCode = providers.gradleProperty("VERSION_CODE").orElse("2").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").orElse("2.0.0").get()
    }

    signingConfigs {
        create("release") {
            storeFile = prop("KEYSTORE_FILE")?.let { file(it) }
            storePassword = prop("KEYSTORE_PASSWORD")
            keyAlias = prop("KEY_ALIAS")
            keyPassword = prop("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
