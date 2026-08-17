import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ---------------------------------------------------------------------
// FIX-5: firma release real desde keystore.properties (NO versionado).
// Plantilla: keystore.properties.example en la raíz del repo.
// El keystore lo genera Gustavo localmente y NUNCA se commitea:
//   keytool -genkeypair -v -keystore rusertech-release.jks -alias rusertech \
//           -keyalg RSA -keysize 4096 -validity 10000
// ---------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.rusertech.mobile"
    compileSdk = 36  // Android 16 (Baklava) — obligatorio en Play Console desde 31/08/2026

    defaultConfig {
        applicationId = "com.rusertech.mobile"
        minSdk = 26
        targetSdk = 36  // Ver Sección 31 — comportamiento edge-to-edge por defecto en API 36+
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ---------------------------------------------------------------------
    // URL del backend — ÚNICO lugar donde vive. Nunca hardcodear en el código.
    // Hoy debug y release apuntan al mismo deployment en vivo: no hay backend
    // local que levantar. Cuando el dominio propio api.rusertech.com esté
    // activo, el swap son estas DOS líneas y nada más.
    // La barra final es obligatoria: Retrofit la necesita para el baseUrl.
    // ---------------------------------------------------------------------
    signingConfigs {
        create("release") {
            // Solo se configura si el archivo existe; si falta, el guard de
            // más abajo hace FALLAR el build release con un mensaje claro —
            // jamás caer en silencio a la firma debug.
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://rusertechmobileapi.vercel.app/\"")
        }
        release {
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://rusertechmobileapi.vercel.app/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// FIX-5: guard del build release. Sin keystore.properties, cualquier tarea
// de release FALLA acá con instrucciones — nunca un APK "release" firmado
// con la clave debug rumbo a un tester o a Play Console.
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any {
        it.project == project && it.name.contains("Release") &&
            (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("install"))
    }
    if (releaseRequested && !keystorePropsFile.exists()) {
        throw GradleException(
            """
            |==========================================================================
            |Falta keystore.properties en la raíz del repo: el build RELEASE se aborta.
            |(La alternativa silenciosa era firmar con la clave debug — inaceptable.)
            |
            |1. Generá el keystore (una sola vez, backup en dos lugares seguros):
            |   keytool -genkeypair -v -keystore rusertech-release.jks -alias rusertech \
            |           -keyalg RSA -keysize 4096 -validity 10000
            |2. Copiá keystore.properties.example a keystore.properties y completalo.
            |3. keystore.properties y *.jks están en .gitignore: NUNCA los commitees.
            |==========================================================================
            """.trimMargin()
        )
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navegación
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Ubicación
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Red
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
}
