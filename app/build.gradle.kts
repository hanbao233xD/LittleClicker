import org.gradle.api.GradleException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningPropertiesFile = rootProject.file("keystore/release-signing.properties")
if (!releaseSigningPropertiesFile.exists()) {
    throw GradleException("Missing release signing file: ${releaseSigningPropertiesFile.path}")
}
val releaseSigningProperties = Properties().apply {
    releaseSigningPropertiesFile.inputStream().use { load(it) }
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
            storeFile = rootProject.file(
                releaseSigningProperties.getProperty("storeFile")
                    ?: throw GradleException("Missing storeFile in ${releaseSigningPropertiesFile.path}")
            )
            storePassword = releaseSigningProperties.getProperty("storePassword")
                ?: throw GradleException("Missing storePassword in ${releaseSigningPropertiesFile.path}")
            keyAlias = releaseSigningProperties.getProperty("keyAlias")
                ?: throw GradleException("Missing keyAlias in ${releaseSigningPropertiesFile.path}")
            keyPassword = releaseSigningProperties.getProperty("keyPassword")
                ?: throw GradleException("Missing keyPassword in ${releaseSigningPropertiesFile.path}")
        }
    }

    defaultConfig {
        applicationId = "com.example.littleclicker"
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "1.7"

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
