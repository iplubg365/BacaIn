plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bacain.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bacain.app"
        minSdk = 26
        targetSdk = 34
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.$run"
    }

    signingConfigs {
        create("bacain") {
            storeFile = file("bacain.jks")
            storePassword = "bacain123"
            keyAlias = "bacain"
            keyPassword = "bacain123"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("bacain") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("bacain")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
