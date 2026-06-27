import org.gradle.api.GradleException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
}

var releaseStoreFile: java.io.File
var releaseStorePassword: String
var releaseKeyAlias: String
var releaseKeyPassword: String

val envKeystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
val envStorePassword = System.getenv("SIGNING_STORE_PASSWORD")
val envKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
val envKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")

if (envKeystorePath != null && envStorePassword != null && envKeyAlias != null && envKeyPassword != null) {
    // CI 环境：从环境变量读取 keystore 文件路径（由 workflow 提前解码生成）
    releaseStoreFile = rootProject.file(envKeystorePath)
    releaseStorePassword = envStorePassword
    releaseKeyAlias = envKeyAlias
    releaseKeyPassword = envKeyPassword
} else {
    // 本地环境：从 properties 文件读取
    val releaseSigningPropertiesFile = rootProject.file("keystore/release-signing.properties")
    if (!releaseSigningPropertiesFile.exists()) {
        throw GradleException("Missing release signing file: ${releaseSigningPropertiesFile.path}")
    }
    val releaseSigningProperties = Properties().apply {
        releaseSigningPropertiesFile.inputStream().use { load(it) }
    }
    releaseStoreFile = rootProject.file(
        releaseSigningProperties.getProperty("storeFile")
            ?: throw GradleException("Missing storeFile in ${releaseSigningPropertiesFile.path}")
    )
    releaseStorePassword = releaseSigningProperties.getProperty("storePassword")
        ?: throw GradleException("Missing storePassword in ${releaseSigningPropertiesFile.path}")
    releaseKeyAlias = releaseSigningProperties.getProperty("keyAlias")
        ?: throw GradleException("Missing keyAlias in ${releaseSigningPropertiesFile.path}")
    releaseKeyPassword = releaseSigningProperties.getProperty("keyPassword")
        ?: throw GradleException("Missing keyPassword in ${releaseSigningPropertiesFile.path}")
}

android {
    namespace = "com.example.littleclicker"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    defaultConfig {
        applicationId = "com.example.littleclicker"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.navigationevent:navigationevent-compose-android:1.0.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
