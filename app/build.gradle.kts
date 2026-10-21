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
    namespace = "jp.polidog.dotlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.polidog.dotlauncher"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "TYPESAFE_API_KEY", "\"\"")
    }
    // アップロード鍵は keystore.properties（git に入れない）か環境変数から。無ければ release は署名しない
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(key: String): String? = keystoreProps.getProperty(key) ?: System.getenv("SIGNING_" + key.uppercase())
    signingConfigs {
        create("upload") {
            signingValue("storeFile")?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("storePassword")
            keyAlias = signingValue("keyAlias")
            keyPassword = signingValue("keyPassword")
        }
    }
    buildTypes {
        debug {
            // 自分の端末用。Jev のキーを local.properties か環境変数から初期値として入れる（設定画面で上書きできる）
            val jevKey = localProps.getProperty("TYPESAFE_API_KEY") ?: System.getenv("TYPESAFE_API_KEY") ?: ""
            buildConfigField("String", "TYPESAFE_API_KEY", "\"$jevKey\"")
        }
        release {
            isMinifyEnabled = false
            // 配るものにはキーを入れない。ユーザーが設定画面で自分のキーを入れる
            if (signingValue("storeFile") != null) signingConfig = signingConfigs.getByName("upload")
        }
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
