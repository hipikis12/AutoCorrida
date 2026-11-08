import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Chave fixa de assinatura:
//  - no PC: arquivo app/keystore.properties + app/autocorrida.jks (fora do Git)
//  - no GitHub: secrets KEYSTORE_BASE64 e KEYSTORE_PASSWORD (o workflow cria o arquivo)
val chaveProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val senhaChave: String? = chaveProps.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
val arquivoChave = file(chaveProps.getProperty("storeFile") ?: "autocorrida.jks")
val temChave = senhaChave != null && arquivoChave.exists()

android {
    namespace = "com.charles.autocorrida"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.charles.autocorrida"
        minSdk = 26
        targetSdk = 34
        // No GitHub cada build ganha um número maior, então a atualização instala por cima.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        create("fixa") {
            if (temChave) {
                storeFile = arquivoChave
                storePassword = senhaChave
                keyAlias = chaveProps.getProperty("keyAlias") ?: "autocorrida"
                keyPassword = chaveProps.getProperty("keyPassword") ?: senhaChave
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (temChave) signingConfigs.getByName("fixa")
                            else signingConfigs.getByName("debug") // sem chave: ainda compila
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
