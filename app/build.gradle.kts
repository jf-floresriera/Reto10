import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// App token de datos.gov.co: se lee de local.properties (datos.appToken=...) o de la
// variable de entorno DATOS_APP_TOKEN (secreto de GitHub Actions). Nunca va en el repositorio.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val appToken: String = (localProps.getProperty("datos.appToken") ?: System.getenv("DATOS_APP_TOKEN") ?: "")
    .filter { it.isLetterOrDigit() }

android {
    namespace = "io.github.jfloresriera.reto10"
    compileSdk = 35

    defaultConfig {
        // No usar "com.example.*": algunos servicios bloquean ese identificador.
        applicationId = "io.github.jfloresriera.reto10"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "DATOS_APP_TOKEN", "\"$appToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Red (el JSON se procesa con org.json, que viene en el SDK de Android)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Mapa vectorial acelerado por GPU (capa coroplética de municipios)
    implementation("org.maplibre.gl:android-sdk:13.0.0")
}
