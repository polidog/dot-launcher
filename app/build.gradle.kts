import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "dev.polidog.simplelauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.polidog.simplelauncher"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // Jev の曖昧検索に使う。local.properties か環境変数から。無ければ曖昧検索は出ない
        val jevKey = localProps.getProperty("TYPESAFE_API_KEY") ?: System.getenv("TYPESAFE_API_KEY") ?: ""
        buildConfigField("String", "TYPESAFE_API_KEY", "\"$jevKey\"")
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
}
