plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.charles.autocorrida"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.charles.autocorrida"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Assina com a chave de debug para facilitar a instalação (troque por uma sua se publicar)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Sem dependências externas: só o framework do Android -> APK minúsculo e rápido.
dependencies { }
