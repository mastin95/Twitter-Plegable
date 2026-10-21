plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.mastin95.pliegue"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.mastin95.pliegue"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Para saber qué APK hay instalado (Ajustes → "Esta versión"): el número de ejecución de
        // GitHub Actions, el commit y la hora de compilar; en un ordenador, "local".
        buildConfigField("String", "BUILD", "\"${System.getenv("GITHUB_RUN_NUMBER") ?: "local"}\"")
        buildConfigField("String", "COMMIT", "\"${(System.getenv("GITHUB_SHA") ?: "").take(7)}\"")
        buildConfigField("String", "BUILT", "\"${java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Madrid")).format(java.time.format.DateTimeFormatter.ofPattern("d/M HH:mm"))}\"")
    }

    // Clave de depuración fija y compartida: sin ella cada compilación en GitHub Actions firma con una
    // clave nueva y Android obliga a desinstalar la versión anterior (y con ella la sesión de X).
    // Es la clave de depuración de siempre de Android (contraseña "android"); no vale para publicar.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.webkit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Fotos y avatares de X en la interfaz propia.
    implementation(libs.coil.compose)
    // Vídeos y GIF de X con reproductor propio (ExoPlayer; HLS para las listas .m3u8 que manda X).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    testImplementation(libs.junit)
    // En las pruebas de JVM el org.json de Android es solo un esqueleto; este es el de verdad.
    testImplementation(libs.org.json)
}
