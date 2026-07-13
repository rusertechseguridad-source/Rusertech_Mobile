# Rusertech Mobile — Especificación de Implementación v1.1 COMPLETO

> **Tipo de documento:** Especificación de implementación lista para producción — AUTOCONTENIDO
> **Audiencia:** Desarrolladores Android senior + agentes de código AI (Claude Code, Antigravity, Codex)
> **Objetivo:** Construir el proyecto Android completo desde cero en una sola pasada
> **Plataforma:** Android-first. iOS no incluido (ver Sección 27).
> **Integración Backend:** La app mobile actúa como un HUB más dentro del pipeline de Rusertech Web (Opción A). Toda la telemetría se escribe en la misma tabla `telemetry` de PostgreSQL. Ver Sección 28.

---

## Cómo usar este documento

### Para desarrolladores humanos
Leé de forma secuencial. Cada sección corresponde a un paso lógico de la construcción. Los bloques de código están anotados con la ruta exacta del archivo. Después de cada sección hay un bloque **VERIFICAR** — ejecutá esas comprobaciones antes de avanzar.

### Para agentes de código AI
Procesá las secciones en orden del 1 al 28. Cada bloque de código comienza con `// ruta/al/archivo.ext` indicando dónde va. No saltear los bloques `### Verificar` — son compuertas, no sugerencias.

### Convenciones
- `app/` = raíz del módulo Android
- Paquete base: `com.rusertech.mobile.*`
- Rutas de archivos relativas a `app/src/main/java/com/rusertech/mobile/`
- **El código fuente (nombres de variables, clases, funciones) está en inglés** — es la convención estándar de Kotlin/Android
- **Toda la prosa, comentarios y documentación están en español**

---

## Tabla de Contenidos

| # | Sección | Propósito |
|---|---------|-----------|
| 1 | Visión del Producto | Qué estamos construyendo |
| 2 | Stack Técnico y Versiones | Versiones fijadas |
| 3 | Inicialización del Proyecto | Crear el proyecto Android |
| 4 | Configuración de Build | Archivos Gradle |
| 5 | Android Manifest | Permisos y componentes |
| 6 | Recursos y Assets | Logo, íconos, strings |
| 7 | Sistema de Diseño | Tema, colores, tipografía |
| 8 | Capa de Dominio | Modelos y tipos |
| 9 | Capa de Datos — Local | Room database, DataStore |
| 10 | Capa de Datos — Remoto | API hacia el backend (formato HUB) |
| 11 | Capa de Datos — Repositorios | Lógica de negocio y traducción de payloads |
| 12 | Utilidades | Helpers, validadores LATAM |
| 13 | Capa de Servicio | Foreground service, ubicación |
| 14 | Sincronización en Background | WorkManager, BootReceiver |
| 15 | Inyección de Dependencias | Módulos Hilt |
| 16 | UI — Registro | Primera pantalla |
| 17 | UI — Tracking | Pantalla principal |
| 18 | UI — Eventos | SOS y eventos |
| 19 | Navegación | Grafo de navegación |
| 20 | Application y MainActivity | Puntos de entrada |
| 21 | Flujo de Permisos | Solicitud en dos pasos |
| 22 | Testing | Tests unitarios e integración |
| 23 | Contrato del Backend | Integración con pipeline Rusertech Web |
| 24 | Build y Release | Firma, R8, publicación |
| 25 | Checklist de Producción | Validación pre-lanzamiento |
| **26** | **Compatibilidad OEM y Batería** | **Xiaomi, Samsung, Doze** |
| **27** | **Soporte iOS (Fase futura)** | **Evaluación y separación** |
| **28** | **Integración con Rusertech Web** | **Opción A: Mobile como HUB** |
| **29** | **Captura de Fotos de Carga** | **Adjuntos multimedia vinculados al viaje** |
| **30** | **Roadmap v1.2** | **QR de configuración, desconexión remota FCM, odómetro** |
| **31** | **Publicación en Play Store** | **Cuenta, targetSdk 36, declaración de ubicación, Data Safety** |

> **Nota de versión:** Esta versión agrega manejo diferenciado de revocación de acceso (401 = advertencia sin detener tracking, 403 = detención automática — Sección 10.1 y 13), captura de fotos de carga (Sección 29), `targetSdk`/`compileSdk` actualizado a 36 por requisito de Play Console vigente desde el 31/08/2026 (Secciones 2 y 4), y el checklist completo de publicación (Sección 31). Ver Sección 30 para funcionalidades evaluadas pero no incluidas en esta versión.

---

## SECCIÓN 1 — Visión del Producto

### Qué hace
Rusertech Mobile es una app de tracking GPS de baja fricción para trabajadores de seguridad y logística en toda Latinoamérica. El usuario se identifica con **documento de identidad + patente del vehículo** — sin email, sin contraseña, sin flujo de registro. Una vez registrado (una sola vez, almacenado localmente), la app rastrea GPS en segundo plano y permite al usuario disparar eventos (SOS, solicitud de contacto, checkpoint, incidente).

### Integración con Rusertech Web
La app mobile **no tiene backend propio**. Actúa como un HUB más dentro del pipeline existente de Rusertech Web:
- Se registra como un `avl_user` de tipo `mobile_app` en el dashboard web
- Recibe una API Key propia
- Envía telemetría al mismo endpoint `POST /api/v1/telemetry/ingest`
- Los datos se escriben en la misma tabla `telemetry` de PostgreSQL
- Los eventos (SOS, checkpoint) pasan por el mismo EventEngine
- El dashboard web ve los datos de la mobile en tiempo real

### Principios fundamentales
1. **Offline-first** — cada dato se persiste localmente antes de cualquier intento de red
2. **Consciente de batería** — intervalos adaptativos, filtros de precisión, deduplicación
3. **Resiliente** — sobrevive reinicios, kills de proceso y falta de red
4. **Preparado para LATAM** — acepta formatos variados de documentos y patentes de cualquier país de la región
5. **Pipeline unificado** — misma tabla de telemetría, mismo motor de eventos, mismo dashboard

### Flujo de usuario
```
Abrir app → Registrarse (documento + patente) → Conceder permisos de ubicación →
Presionar "Iniciar seguimiento" → El servicio corre en segundo plano →
Disparar eventos según necesidad (SOS, checkpoint, etc.) →
Presionar "Detener seguimiento" cuando termine
```

### Fuera del alcance
- Autenticación de usuario vía email/contraseña
- Multi-usuario / cambio de cuenta en el mismo dispositivo
- Mapas dentro de la app
- Push notifications recibidas del servidor
- Backend propio (usa el pipeline de Rusertech Web)

---

## SECCIÓN 2 — Stack Técnico y Versiones

Todas las versiones están fijadas. No actualizar sin modificar esta sección.

| Componente | Versión | Propósito |
|-----------|---------|-----------|
| Kotlin | 1.9.24 | Lenguaje |
| Android Gradle Plugin | 8.9.1 | Build (mínimo requerido para compileSdk 36) |
| Compile SDK | 36 | Target Android 16 — **obligatorio en Play Console desde el 31/08/2026** |
| Min SDK | 26 | Android 8.0 (~98% mercado LATAM) |
| Target SDK | 36 | Android 16 — ver Sección 31 |
| Jetpack Compose BOM | 2024.08.00 | UI |
| Hilt | 2.51.1 | DI |
| Room | 2.6.1 | DB local |
| DataStore | 1.1.1 | Preferencias |
| WorkManager | 2.9.1 | Sync background |
| Play Services Location | 21.3.0 | FusedLocationProvider |
| Retrofit | 2.11.0 | HTTP |
| OkHttp | 4.12.0 | Cliente HTTP |
| Kotlinx Serialization | 1.7.1 | JSON |
| Navigation Compose | 2.7.7 | Navegación |
| KSP | 1.9.24-1.0.20 | Anotaciones |

### VERIFICAR
- [ ] Android Studio Ladybug (2024.2) o más nuevo instalado — versiones previas no soportan AGP 8.9+
- [ ] JDK 17 configurado
- [ ] Gradle Wrapper en 8.11 o superior (requerido por AGP 8.9.1 — ver `gradle-wrapper.properties` en Sección 4)
- [ ] SDK Platform Android 16 (Baklava, API 36) instalado vía SDK Manager

---

## SECCIÓN 3 — Inicialización del Proyecto

### Paso 1 — Crear el proyecto
En Android Studio: File → New → New Project → Empty Activity (Compose)
- Nombre: `Rusertech Mobile`
- Paquete: `com.rusertech.mobile`
- Kotlin DSL (.kts)
- Min SDK: API 26

### Paso 2 — Estructura de carpetas
```
com/rusertech/mobile/
├── di/
├── data/
│   ├── local/
│   │   ├── db/
│   │   └── prefs/
│   ├── remote/
│   │   ├── api/
│   │   └── sync/
│   └── repository/
├── domain/model/
├── service/
├── ui/
│   ├── theme/
│   ├── registration/
│   ├── tracking/
│   ├── events/
│   ├── splash/
│   ├── navigation/
│   └── common/
└── util/
```

### Paso 3 — Eliminar archivos generados
Borrar `ui.theme/Color.kt`, `Theme.kt`, `Type.kt` auto-generados.

### VERIFICAR
- [ ] El proyecto compila
- [ ] La estructura coincide con el árbol
- [ ] No queda código de ejemplo

---

## SECCIÓN 4 — Configuración de Build

### Archivo: `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Rusertech Mobile"
include(":app")
```

### Archivo: `gradle/wrapper/gradle-wrapper.properties`
```properties
# AGP 8.9.1 requiere Gradle 8.11 o superior — sin esto, el build falla
# con un error de incompatibilidad de versiones al primer sync.
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

### Archivo: `build.gradle.kts` (raíz)
```kotlin
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
}
```

### Archivo: `app/build.gradle.kts`
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
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
        // URL del backend Rusertech Web — mismo servidor que el dashboard
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://api.rusertech.com/\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://staging-api.rusertech.com/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
```

### Archivo: `app/proguard-rules.pro`
```proguard
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.rusertech.mobile.**$$serializer { *; }
-keepclassmembers class com.rusertech.mobile.** { *** Companion; }
-keepclasseswithmembers class com.rusertech.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keep class com.rusertech.mobile.data.remote.api.** { *; }
-keep class com.rusertech.mobile.domain.model.** { *; }
```

### VERIFICAR
- [ ] `./gradlew clean build` completa (fallará por archivos faltantes — esperado)
- [ ] `BuildConfig.BACKEND_BASE_URL` es referenciable
- [ ] El primer sync no tira error de "Gradle version incompatible" (confirma que `gradle-wrapper.properties` quedó en 8.11.1)
- [ ] `compileSdk = 36` no dispara warning de "unsupported compileSdk" (confirma AGP 8.9.1 activo)

---

## SECCIÓN 5 — Android Manifest

### Archivo: `app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.CAMERA" />

    <uses-feature android:name="android.hardware.location.gps" android:required="false" />
    <uses-feature android:name="android.hardware.location.network" android:required="false" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:name=".RusertechApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.RusertechMobile"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:theme="@style/Theme.RusertechMobile">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.TrackingService"
            android:foregroundServiceType="location"
            android:exported="false" />

        <receiver
            android:name=".service.BootReceiver"
            android:exported="false"
            android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>

        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

        <!-- Sección 29: FileProvider para que la cámara del sistema escriba las fotos de carga -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

### Archivo: `app/src/main/res/xml/file_paths.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cargo_photos" path="cargo_photos/" />
</paths>
```

> **Nota:** no se pide `READ_EXTERNAL_STORAGE` ni `READ_MEDIA_IMAGES`. Las fotos se escriben en el directorio privado de caché de la propia app vía `FileProvider`, así que no se necesita permiso de almacenamiento en ninguna versión de Android — la foto nunca toca la galería pública del dispositivo. Ver detalle de uso en Sección 29.

### Archivo: `app/src/main/res/xml/data_extraction_rules.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" />
        <exclude domain="database" />
        <exclude domain="file" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" />
        <exclude domain="database" />
        <exclude domain="file" />
    </device-transfer>
</data-extraction-rules>
```

### Archivo: `app/src/main/res/xml/backup_rules.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" />
    <exclude domain="database" />
</full-backup-content>
```

### VERIFICAR
- [ ] El Manifest valida sin errores
- [ ] Todos los permisos coinciden con la Sección 21

---

## SECCIÓN 6 — Recursos y Assets

### Archivo: `app/src/main/res/values/strings.xml`
```xml
<resources>
    <string name="app_name">Rusertech Mobile</string>
    <string name="app_tagline">Seguridad &amp; Logística</string>
    <string name="register_title">Ingresá tus datos para comenzar</string>
    <string name="register_dni_label">Documento de identidad</string>
    <string name="register_dni_placeholder">DNI, RUT, CPF, cédula…</string>
    <string name="register_plate_label">Patente del vehículo</string>
    <string name="register_plate_placeholder">Ej: AB123CD</string>
    <string name="register_save">Guardar y continuar</string>
    <string name="register_disclaimer">Tus datos se guardan localmente en el dispositivo</string>
    <string name="tracking_start">Iniciar seguimiento</string>
    <string name="tracking_stop">Detener seguimiento</string>
    <string name="tracking_active">Activo</string>
    <string name="tracking_stopped">Detenido</string>
    <string name="tracking_status_gps">GPS</string>
    <string name="tracking_status_network">Red</string>
    <string name="tracking_status_tracking">Tracking</string>
    <string name="tracking_status_battery">Batería</string>
    <string name="tracking_status_pending">Pendientes</string>
    <string name="events_title">Eventos</string>
    <string name="events_sos">SOS — Pedido de ayuda</string>
    <string name="events_sos_sent">SOS enviado</string>
    <string name="events_communication">Solicitar contacto</string>
    <string name="events_checkpoint">Checkpoint</string>
    <string name="events_incident">Incidente</string>
    <string name="events_recent_history">Historial reciente</string>
    <string name="events_no_events">Sin eventos registrados</string>
    <string name="permission_location_title">Permiso de ubicación</string>
    <string name="permission_location_message">Necesitamos acceso a tu ubicación para registrar tu recorrido durante el turno.</string>
    <string name="permission_background_title">Ubicación en segundo plano</string>
    <string name="permission_background_message">Para que el seguimiento siga funcionando con la app cerrada, seleccioná \"Permitir todo el tiempo\" en el siguiente paso.</string>
    <string name="permission_grant">Conceder permiso</string>
    <string name="permission_open_settings">Abrir configuración</string>
    <string name="service_channel_name">Seguimiento GPS</string>
    <string name="service_channel_description">Tracking activo de ubicación</string>
    <string name="service_notification_title">Rusertech Mobile</string>
    <string name="service_notification_text_active">Seguimiento activo</string>
    <string name="service_notification_action_stop">Detener</string>
    <string name="error_invalid_dni">El documento debe tener entre 6 y 20 caracteres</string>
    <string name="error_invalid_plate">La patente debe tener entre 6 y 8 caracteres</string>
</resources>
```

### Archivo: `app/src/main/res/values/colors.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="deep_space_top">#FF1F2A5A</color>
    <color name="deep_space_bottom">#FF2B2F6E</color>
    <color name="tech_glow_green">#FF7CFF3C</color>
    <color name="tech_glow_cyan">#FF33E1A1</color>
    <color name="tech_glow_blue">#FF2AB3FF</color>
    <color name="text_primary">#FFE5E7EB</color>
    <color name="text_secondary">#FF9CA3AF</color>
    <color name="text_muted">#FF6B7280</color>
    <color name="sos_red">#FFEF4444</color>
    <color name="warning_amber">#FFF59E0B</color>
</resources>
```

### Archivo: `app/src/main/res/values/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.RusertechMobile" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@color/deep_space_top</item>
        <item name="android:navigationBarColor">@color/deep_space_bottom</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
```

### Logo e ícono adaptativo
Copiar `logo_rusertech.png` (1024x1024) en:
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png` (432x432)
- `app/src/main/res/drawable/rusertech_logo.png` (1024x1024)

En Android Studio: clic derecho `res` → Image Asset → Foreground = logo → Background = `#1F2A5A`.

### Archivo: `app/src/main/res/drawable/ic_notification.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@android:color/white">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,2L4,5v6c0,5 3.5,9.5 8,11 4.5,-1.5 8,-6 8,-11V5L12,2zM12,7l4,3v3l-4,3 -4,-3v-3l4,-3z"/>
</vector>
```

### VERIFICAR
- [ ] Ícono muestra escudo Rusertech sobre fondo oscuro
- [ ] `R.string.app_name` = "Rusertech Mobile"

---

## SECCIÓN 7 — Sistema de Diseño (Tema Compose)

### Archivo: `ui/theme/Color.kt`
```kotlin
package com.rusertech.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// Fondos Deep Space — idénticos a tokens web: bgStart / bgEnd
val DeepSpaceTop = Color(0xFF1F2A5A)
val DeepSpaceBottom = Color(0xFF2B2F6E)

// Acentos Tech Glow — idénticos a tokens web: accentGreen / accentMint / accentBlue
val TechGlowGreen = Color(0xFF7CFF3C)
val TechGlowCyan = Color(0xFF33E1A1)
val TechGlowBlue = Color(0xFF2AB3FF)

// Texto — idénticos a tokens web: textPrimary / textSecondary / textMuted
val TextPrimary = Color(0xFFE5E7EB)
val TextSecondary = Color(0xFF9CA3AF)
val TextMuted = Color(0xFF6B7280)

// Semánticos
val SOSRed = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)
val SuccessGreen = Color(0xFF33E1A1)
val InfoBlue = Color(0xFF2AB3FF)

// Superficies (overlays alfa)
val SurfaceCard = Color(0x0FFFFFFF)
val SurfaceBorder = Color(0x1AFFFFFF)
val SurfaceInput = Color(0x14FFFFFF)
val SurfaceElevated = Color(0x1FFFFFFF)

// Contraste de ícono sobre gradiente Tech Glow
val IconOnGlow = DeepSpaceTop
```

### Archivo: `ui/theme/Brushes.kt`
```kotlin
package com.rusertech.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush

@Composable
fun deepSpaceGradient(): Brush = remember {
    Brush.verticalGradient(colors = listOf(DeepSpaceTop, DeepSpaceBottom))
}

@Composable
fun techGlowGradient(): Brush = remember {
    Brush.linearGradient(colors = listOf(TechGlowGreen, TechGlowCyan, TechGlowBlue))
}
```

### Archivo: `ui/theme/Type.kt`
```kotlin
package com.rusertech.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RusertechTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.W500, color = TextPrimary),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.W500, color = TextPrimary),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W500, color = TextPrimary),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W500, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = TextSecondary),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = TextMuted),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W500, color = TextPrimary),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = TextSecondary),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = TextMuted)
)
```

### Archivo: `ui/theme/Theme.kt`
```kotlin
package com.rusertech.mobile.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RusertechColors = darkColorScheme(
    primary = TechGlowCyan, onPrimary = DeepSpaceTop,
    secondary = TechGlowBlue, onSecondary = DeepSpaceTop,
    tertiary = TechGlowGreen, onTertiary = DeepSpaceTop,
    background = DeepSpaceTop, onBackground = TextPrimary,
    surface = DeepSpaceBottom, onSurface = TextPrimary,
    surfaceVariant = SurfaceCard, onSurfaceVariant = TextSecondary,
    error = SOSRed, onError = TextPrimary, outline = SurfaceBorder
)

@Composable
fun RusertechTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepSpaceTop.toArgb()
            window.navigationBarColor = DeepSpaceBottom.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = RusertechColors, typography = RusertechTypography, content = content)
}
```

### VERIFICAR
- [ ] Los imports resuelven
- [ ] La barra de estado usa Deep Space

---

## SECCIÓN 8 — Capa de Dominio

### Archivo: `domain/model/UserIdentity.kt`
```kotlin
package com.rusertech.mobile.domain.model

/**
 * Identifica a un conductor en cualquier país de LATAM.
 * documentId: DNI (AR), CPF (BR), RUT (CL), Cédula (CO), INE (MX), etc. 6-20 chars alfanuméricos.
 * plate: Patente del vehículo. 6-8 chars. Mapea a vehicles.plate y al campo Asset del HUB.
 * avlUserCode: Código del avl_user asignado a esta flota mobile. Provisto por el operador.
 * apiKey: API Key del avl_user mobile. Provista por el operador.
 */
data class UserIdentity(
    val documentId: String,
    val plate: String,
    val avlUserCode: String = "",
    val apiKey: String = ""
)
```

### Archivo: `domain/model/LocationPoint.kt`
```kotlin
package com.rusertech.mobile.domain.model

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,         // m/s
    val heading: Float,       // grados 0-360
    val altitude: Double,
    val battery: Int,         // 0..100
    val timestamp: Long       // milisegundos epoch
) {
    fun speedKmh(): Float = speed * 3.6f
}
```

### Archivo: `domain/model/TrackingEvent.kt`
```kotlin
package com.rusertech.mobile.domain.model

/**
 * Tipos de evento que la app puede producir.
 * El `code` es lo que se envía en el campo Code del HubRawPayload.
 * El operador debe mapear estos códigos en el diccionario del avl_user mobile
 * dentro del dashboard Rusertech Web.
 */
enum class EventType(val code: String, val displayName: String) {
    SOS("MOB_SOS", "Pedido de ayuda (SOS)"),
    COMMUNICATION_REQUEST("MOB_COMM", "Solicitud de contacto"),
    CHECKPOINT("MOB_CHKPT", "Checkpoint"),
    INCIDENT("MOB_INCIDENT", "Incidente"),
    VEHICLE_STOP("MOB_STOP", "Parada del vehículo"),
    LOW_BATTERY("MOB_LOWBAT", "Batería baja");

    companion object {
        fun fromCode(code: String): EventType? = entries.firstOrNull { it.code == code }
    }
}

data class TrackingEvent(
    val id: Long = 0,
    val type: EventType,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val notes: String = "",
    val metadata: Map<String, String> = emptyMap()
)
```

### VERIFICAR
- [ ] Los modelos compilan
- [ ] Los codes de evento usan prefijo `MOB_` para diferenciarse de los códigos HUB

---

## SECCIÓN 9 — Capa de Datos (Local)

### Archivo: `data/local/db/LocationEntity.kt`
```kotlin
package com.rusertech.mobile.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_locations",
    indices = [Index(value = ["isSynced", "timestamp"]), Index(value = ["timestamp"])]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val heading: Float,
    val altitude: Double,
    val battery: Int,
    val timestamp: Long,
    val isSynced: Boolean = false
)
```

### Archivo: `data/local/db/EventEntity.kt`
```kotlin
package com.rusertech.mobile.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracking_events",
    indices = [
        Index(value = ["isSynced", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,              // EventType.code (MOB_SOS, MOB_CHKPT, etc.)
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val notes: String = "",
    val metadataJson: String = "{}",
    val isSynced: Boolean = false
)
```

### Archivo: `data/local/db/LocationDao.kt`
```kotlin
package com.rusertech.mobile.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocationEntity): Long

    @Query("SELECT * FROM pending_locations WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 50): List<LocationEntity>

    @Query("UPDATE pending_locations SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM pending_locations WHERE isSynced = 1 AND timestamp < :before")
    suspend fun purgeSynced(before: Long)

    @Query("SELECT COUNT(*) FROM pending_locations WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}
```

### Archivo: `data/local/db/EventDao.kt`
```kotlin
package com.rusertech.mobile.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EventEntity): Long

    @Query("SELECT * FROM tracking_events WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 30): List<EventEntity>

    @Query("UPDATE tracking_events SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM tracking_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 30): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM tracking_events WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("DELETE FROM tracking_events WHERE isSynced = 1 AND timestamp < :before")
    suspend fun purgeSynced(before: Long)
}
```

### Archivo: `data/local/db/AppDatabase.kt`
```kotlin
package com.rusertech.mobile.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

// version = 2: incluye AttachmentEntity (fotos de carga, Sección 29) desde el inicio.
// Si el proyecto ya tiene usuarios en producción con version = 1, agregar una
// Migration real en vez de subir el número directamente (ver Sección 25).
@Database(
    entities = [LocationEntity::class, EventEntity::class, AttachmentEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun eventDao(): EventDao
    abstract fun attachmentDao(): AttachmentDao
}
```

### Archivo: `data/local/prefs/UserPreferences.kt`
```kotlin
package com.rusertech.mobile.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rusertech.mobile.domain.model.UserIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "rusertech_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DOCUMENT_ID = stringPreferencesKey("document_id")
        val PLATE = stringPreferencesKey("plate")
        val AVL_USER_CODE = stringPreferencesKey("avl_user_code")
        val API_KEY = stringPreferencesKey("api_key")
        val IS_TRACKING = booleanPreferencesKey("is_tracking")
    }

    val userIdentity: Flow<UserIdentity?> = context.dataStore.data.map { prefs ->
        val doc = prefs[Keys.DOCUMENT_ID]
        val plate = prefs[Keys.PLATE]
        val avlCode = prefs[Keys.AVL_USER_CODE] ?: ""
        val apiKey = prefs[Keys.API_KEY] ?: ""
        if (!doc.isNullOrBlank() && !plate.isNullOrBlank())
            UserIdentity(doc, plate, avlCode, apiKey) else null
    }

    val isTracking: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.IS_TRACKING] ?: false
    }

    suspend fun saveIdentity(documentId: String, plate: String, avlUserCode: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOCUMENT_ID] = documentId.trim()
            prefs[Keys.PLATE] = plate.trim().uppercase()
            prefs[Keys.AVL_USER_CODE] = avlUserCode.trim()
            prefs[Keys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun setTracking(active: Boolean) {
        context.dataStore.edit { it[Keys.IS_TRACKING] = active }
    }

    suspend fun snapshot(): UserIdentity? = userIdentity.first()
    suspend fun isTrackingSnapshot(): Boolean = isTracking.first()

    suspend fun clear() { context.dataStore.edit { it.clear() } }
}
```

### VERIFICAR
- [ ] Room genera código sin errores
- [ ] UserPreferences almacena los 4 campos: documentId, plate, avlUserCode, apiKey

---

## SECCIÓN 10 — Capa de Datos (Remoto) — FORMATO HUB

> **CAMBIO CLAVE v1.1:** La app envía al mismo endpoint de ingesta que los HUB GPS.
> El payload sigue el formato `HubRawPayload` definido en el Master Prompt web.
> Header de autenticación: `X-Hub-Api-Key`.

### Archivo: `data/remote/api/HubRawPayload.kt`
```kotlin
package com.rusertech.mobile.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload compatible con el endpoint POST /api/v1/telemetry/ingest
 * del backend Rusertech Web. Formato idéntico al que envían los HUB GPS.
 *
 * La app mobile actúa como un HUB más. El campo Asset = patente del vehículo.
 * El campo User_avl = código del avl_user mobile asignado por el operador.
 * El campo Code = código de evento mobile (MOB_SOS, MOB_CHKPT, etc.)
 *               o null para puntos de telemetría normales.
 */
@Serializable
data class HubRawPayload(
    @SerialName("Asset") val asset: String,                     // = plate
    @SerialName("User_avl") val userAvl: String,                // = avlUserCode
    @SerialName("Date") val date: String,                       // ISO 8601
    @SerialName("Latitude") val latitude: String,
    @SerialName("Longitude") val longitude: String,
    @SerialName("Speed") val speed: String,
    @SerialName("Course") val course: String? = null,           // heading en grados
    @SerialName("Code") val code: String? = null,               // evento mobile o null
    @SerialName("Ignition") val ignition: String? = null,
    @SerialName("Altitude") val altitude: String? = null,
    @SerialName("Odometer") val odometer: String? = null,
    @SerialName("Battery") val battery: String? = null,
    @SerialName("Temperature") val temperature: String? = null,
    @SerialName("Humidity") val humidity: String? = null,
    @SerialName("Direction") val direction: String? = null,
    @SerialName("SerialNumber") val serialNumber: String? = null,
    @SerialName("Shipment") val shipment: String? = null,       // metadata JSON del evento
    @SerialName("SourceTag") val sourceTag: String? = "mobile_app",
    @SerialName("Alert") val alert: String? = null
)
```

### Archivo: `data/remote/api/TrackingApi.kt`
```kotlin
package com.rusertech.mobile.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TrackingApi {

    /**
     * Endpoint ÚNICO de ingesta — mismo que usan los HUB GPS.
     * La autenticación es por header X-Hub-Api-Key (API Key del avl_user mobile).
     */
    @POST("api/v1/telemetry/ingest")
    suspend fun ingest(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Body payload: HubRawPayload
    ): Response<Unit>

    /**
     * Envío en lote — múltiples puntos en un solo request.
     */
    @POST("api/v1/telemetry/ingest/batch")
    suspend fun ingestBatch(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Body payload: List<HubRawPayload>
    ): Response<Unit>
}
```

### VERIFICAR
- [ ] `HubRawPayload` tiene los mismos campos que `HubRawPayload` del Master Prompt web
- [ ] `@SerialName` coincide exactamente con los nombres del backend (Asset, User_avl, Date, etc.)
- [ ] El header `X-Hub-Api-Key` es el mismo que usa el backend para autenticar HUBs

---

### SECCIÓN 10.1 — Manejo de Revocación de Acceso (HTTP 401/403)

> **Por qué esto existe:** El operador puede necesitar desconectar a un conductor de forma remota — por ejemplo, si terminó el turno y olvidó apagar la app, o si hay que dar de baja el acceso de un vehículo. El mecanismo más simple y confiable: el backend responde con un código HTTP específico según el motivo, y la app reacciona distinto en cada caso.

Esto es intencionalmente **reactivo, no proactivo**: no requiere infraestructura de push (FCM) para el caso base. El próximo intento de sync (cada pocos segundos mientras trackea, o cada 15 min vía WorkManager) detecta el código y reacciona. Ver Sección 30 para la variante con push instantáneo.

**Los dos códigos tienen semántica distinta — no se tratan igual:**

| Código | Significado | Comportamiento de la app |
|--------|-------------|---------------------------|
| `403 Forbidden` | El operador desactivó deliberadamente el `avl_user` o revocó la API Key | Detiene el tracking de inmediato, bloquea el botón de "Iniciar seguimiento", muestra banner rojo explicando por qué |
| `401 Unauthorized` | La API Key está mal formada o mal tipeada (típicamente un error de carga en el registro, no una decisión del operador) | **No detiene el tracking.** Sigue guardando todo en la cola local (offline-first, igual que si no hubiera red). Muestra banner ámbar pidiendo al conductor que revise sus credenciales con el operador |

La distinción importa en la práctica: un typo en la API Key durante el registro no debería obligar al conductor a perder todo el turno de tracking — los datos se acumulan localmente sin problema y se sincronizan solos apenas se corrija la key. Una revocación deliberada sí debe cortar todo, porque el operador explícitamente decidió que ese vehículo deje de reportar.

### Archivo: `data/remote/api/AuthEventBus.kt`
```kotlin
package com.rusertech.mobile.data.remote.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canal de eventos de autenticación entre la capa de red y el resto de la app.
 * AuthInterceptor emite acá según el código HTTP recibido:
 * - accessRevoked (403): TrackingService detiene el servicio.
 * - credentialWarning (401): TrackingService NO detiene nada, solo avisa a la UI.
 */
@Singleton
class AuthEventBus @Inject constructor() {
    private val _accessRevoked = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val accessRevoked: SharedFlow<Unit> = _accessRevoked.asSharedFlow()

    private val _credentialWarning = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val credentialWarning: SharedFlow<Unit> = _credentialWarning.asSharedFlow()

    /** 403 — acceso revocado deliberadamente por el operador. */
    fun notifyAccessRevoked() {
        _accessRevoked.tryEmit(Unit)
    }

    /** 401 — API Key mal formada/mal tipeada. No es una revocación. */
    fun notifyCredentialWarning() {
        _credentialWarning.tryEmit(Unit)
    }
}
```

### Archivo: `data/remote/interceptor/AuthInterceptor.kt`
```kotlin
package com.rusertech.mobile.data.remote.interceptor

import com.rusertech.mobile.data.remote.api.AuthEventBus
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepta cada respuesta HTTP y distingue el motivo del rechazo:
 * - 403 → el avl_user fue desactivado o la API Key fue revocada a propósito.
 * - 401 → la API Key está mal formada (típicamente un typo de carga).
 * Cada código dispara un evento distinto en AuthEventBus — ver Sección 10.1.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authEventBus: AuthEventBus
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        when (response.code) {
            403 -> authEventBus.notifyAccessRevoked()
            401 -> authEventBus.notifyCredentialWarning()
        }
        return response
    }
}
```

### VERIFICAR
- [ ] `AuthInterceptor` está registrado en `OkHttpClient.Builder()` (ver Sección 15)
- [ ] Desactivar el `avl_user` en el dashboard web (403) y confirmar que la app detiene el tracking y bloquea el botón de inicio
- [ ] Enviar una API Key mal formada (401) y confirmar que el tracking **sigue activo**, con banner ámbar, y que los puntos se siguen encolando en Room
- [ ] Corregir la API Key sin reiniciar la app y confirmar que el próximo sync exitoso hace desaparecer el banner ámbar

---

## SECCIÓN 11 — Capa de Datos (Repositorios)

> **CAMBIO CLAVE v1.1:** Los repositorios traducen las entidades Room al formato HubRawPayload antes de enviar.

### Archivo: `data/repository/UserRepository.kt`
```kotlin
package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.domain.model.UserIdentity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(private val prefs: UserPreferences) {
    val userIdentity: Flow<UserIdentity?> = prefs.userIdentity
    val isTracking: Flow<Boolean> = prefs.isTracking

    suspend fun saveIdentity(documentId: String, plate: String, avlUserCode: String, apiKey: String) {
        prefs.saveIdentity(documentId, plate, avlUserCode, apiKey)
    }

    suspend fun setTracking(active: Boolean) = prefs.setTracking(active)
    suspend fun snapshot(): UserIdentity? = prefs.snapshot()
    suspend fun isTrackingSnapshot(): Boolean = prefs.isTrackingSnapshot()
    suspend fun logout() = prefs.clear()
}
```

### Archivo: `data/repository/LocationRepository.kt`
```kotlin
package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.db.LocationDao
import com.rusertech.mobile.data.local.db.LocationEntity
import com.rusertech.mobile.data.remote.api.HubRawPayload
import com.rusertech.mobile.data.remote.api.TrackingApi
import com.rusertech.mobile.domain.model.LocationPoint
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.NetworkUtil
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val dao: LocationDao,
    private val api: TrackingApi,
    private val networkUtil: NetworkUtil
) {
    /** Siempre persiste localmente primero. Intento inmediato si hay red. */
    suspend fun saveLocation(identity: UserIdentity, point: LocationPoint) {
        val entity = LocationEntity(
            latitude = point.latitude, longitude = point.longitude,
            accuracy = point.accuracy, speed = point.speed,
            heading = point.heading, altitude = point.altitude,
            battery = point.battery, timestamp = point.timestamp
        )
        val id = dao.insert(entity)

        if (networkUtil.isOnline() && identity.apiKey.isNotBlank()) {
            tryImmediateSend(identity, entity.copy(id = id))
        }
    }

    /** Sincroniza lote de ubicaciones pendientes como HubRawPayload. */
    suspend fun syncPending(identity: UserIdentity): Result<Int> = runCatching {
        if (identity.apiKey.isBlank()) return@runCatching 0
        val pending = dao.getUnsynced(50)
        if (pending.isEmpty()) return@runCatching 0

        val payloads = pending.map { it.toHubPayload(identity) }
        val response = api.ingestBatch(identity.apiKey, payloads)
        if (!response.isSuccessful) {
            throw IllegalStateException("Sync falló con HTTP ${response.code()}")
        }

        dao.markSynced(pending.map { it.id })
        dao.purgeSynced(System.currentTimeMillis() - 86_400_000L)
        pending.size
    }

    fun getUnsyncedCount(): Flow<Int> = dao.getUnsyncedCount()

    private suspend fun tryImmediateSend(identity: UserIdentity, entity: LocationEntity) {
        try {
            val resp = api.ingest(identity.apiKey, entity.toHubPayload(identity))
            if (resp.isSuccessful) dao.markSynced(listOf(entity.id))
        } catch (_: Exception) { /* WorkManager reintentará */ }
    }

    /** Convierte una LocationEntity al formato HubRawPayload del backend web. */
    private fun LocationEntity.toHubPayload(identity: UserIdentity) = HubRawPayload(
        asset = identity.plate,
        userAvl = identity.avlUserCode,
        date = Instant.ofEpochMilli(timestamp)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT),
        latitude = latitude.toString(),
        longitude = longitude.toString(),
        speed = (speed * 3.6f).toString(),  // m/s → km/h para el backend
        course = heading.toInt().toString(),
        code = null,  // Sin evento — es telemetría pura
        battery = battery.toString(),
        altitude = altitude.toString(),
        sourceTag = "mobile_app"
    )
}
```

### Archivo: `data/repository/EventRepository.kt`
```kotlin
package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.db.EventDao
import com.rusertech.mobile.data.local.db.EventEntity
import com.rusertech.mobile.data.remote.api.HubRawPayload
import com.rusertech.mobile.data.remote.api.TrackingApi
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.NetworkUtil
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Los eventos mobile se envían como puntos de telemetría con el campo Code
 * seteado al código del evento (MOB_SOS, MOB_CHKPT, etc.).
 * El EventEngine del backend los procesa vía el diccionario del avl_user.
 */
@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val api: TrackingApi,
    private val networkUtil: NetworkUtil
) {
    suspend fun createEvent(
        type: EventType,
        identity: UserIdentity,
        latitude: Double,
        longitude: Double,
        notes: String = "",
        metadata: Map<String, String> = emptyMap()
    ): Long {
        val entity = EventEntity(
            type = type.code,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis(),
            notes = notes,
            metadataJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer(), metadata
            )
        )
        val id = dao.insert(entity)

        if (networkUtil.isOnline() && identity.apiKey.isNotBlank()) {
            tryImmediateSend(identity, entity.copy(id = id))
        }
        return id
    }

    suspend fun syncPending(identity: UserIdentity): Result<Int> = runCatching {
        if (identity.apiKey.isBlank()) return@runCatching 0
        val pending = dao.getUnsynced(30)
        if (pending.isEmpty()) return@runCatching 0

        // Enviar cada evento como un punto de telemetría con Code
        val payloads = pending.map { it.toHubPayload(identity) }
        val response = api.ingestBatch(identity.apiKey, payloads)
        if (response.isSuccessful) {
            dao.markSynced(pending.map { it.id })
            dao.purgeSynced(System.currentTimeMillis() - 7 * 86_400_000L)
        }
        pending.size
    }

    fun getRecent(): Flow<List<EventEntity>> = dao.getRecent()
    fun getUnsyncedCount(): Flow<Int> = dao.getUnsyncedCount()

    private suspend fun tryImmediateSend(identity: UserIdentity, entity: EventEntity) {
        try {
            val resp = api.ingest(identity.apiKey, entity.toHubPayload(identity))
            if (resp.isSuccessful) dao.markSynced(listOf(entity.id))
        } catch (_: Exception) { /* WorkManager reintentará */ }
    }

    /** Convierte un EventEntity al formato HubRawPayload con el Code del evento. */
    private fun EventEntity.toHubPayload(identity: UserIdentity) = HubRawPayload(
        asset = identity.plate,
        userAvl = identity.avlUserCode,
        date = Instant.ofEpochMilli(timestamp)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT),
        latitude = latitude.toString(),
        longitude = longitude.toString(),
        speed = "0",
        code = type,  // MOB_SOS, MOB_CHKPT, MOB_COMM, MOB_INCIDENT, etc.
        shipment = if (notes.isNotBlank()) notes else null,  // Notas van en Shipment
        sourceTag = "mobile_app"
    )
}
```

### VERIFICAR
- [ ] Los repositorios generan HubRawPayload, no payloads custom
- [ ] El campo `asset` = plate, `userAvl` = avlUserCode, `code` = tipo de evento
- [ ] El header `X-Hub-Api-Key` se pasa en cada llamada

---

## SECCIÓN 12 — Utilidades

### Archivo: `util/NetworkUtil.kt`
```kotlin
package com.rusertech.mobile.util

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkUtil @Inject constructor(private val cm: ConnectivityManager) {
    fun isOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    val isOnlineFlow: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(isOnline()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        cm.registerNetworkCallback(request, callback)
        trySend(isOnline())
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
```

### Archivo: `util/BatteryUtil.kt`
```kotlin
package com.rusertech.mobile.util

import android.content.Context
import android.os.BatteryManager

object BatteryUtil {
    fun getLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(-1, 100)
    }
}
```

### Archivo: `util/IdentityValidator.kt`
```kotlin
package com.rusertech.mobile.util

/**
 * Valida documentos de identidad de toda LATAM.
 * Acepta 6-20 caracteres alfanuméricos tras eliminar separadores.
 * AR DNI (7-8 dígitos), BR CPF (11 dígitos), CL RUT (7-9+DV),
 * CO Cédula (8-10), MX CURP (18), PE DNI (8), UY CI (7-8), VE Cédula (7-9).
 */
object IdentityValidator {
    private const val MIN = 6
    private const val MAX = 20

    fun normalize(input: String): String = input.trim().uppercase().filter { it.isLetterOrDigit() }

    fun isValid(input: String): Boolean = normalize(input).length in MIN..MAX

    fun errorOrNull(input: String): String? {
        val n = normalize(input)
        return when {
            n.isEmpty() -> "El documento es obligatorio"
            n.length < MIN -> "Mínimo $MIN caracteres"
            n.length > MAX -> "Máximo $MAX caracteres"
            else -> null
        }
    }
}
```

### Archivo: `util/PlateValidator.kt`
```kotlin
package com.rusertech.mobile.util

/**
 * Valida patentes de toda LATAM.
 * Acepta 6-8 caracteres alfanuméricos tras eliminar separadores.
 * Mercosur AR/BR/UY/PY (AB123CD, 7), clásica AR (ABC123, 6),
 * BR pre-Mercosur (ABC1234, 7), CL (ABCD12, 6), CO (ABC123, 6),
 * MX (ABC1234, 7), PE (A1B234, 6), BO (1234ABC, 7), EC (ABC1234, 7).
 */
object PlateValidator {
    private const val MIN = 6
    private const val MAX = 8

    fun normalize(input: String): String = input.trim().uppercase().filter { it.isLetterOrDigit() }

    fun isValid(input: String): Boolean = normalize(input).length in MIN..MAX

    fun errorOrNull(input: String): String? {
        val n = normalize(input)
        return when {
            n.isEmpty() -> "La patente es obligatoria"
            n.length < MIN -> "Mínimo $MIN caracteres"
            n.length > MAX -> "Máximo $MAX caracteres"
            else -> null
        }
    }
}
```

### Archivo: `util/BatteryOptimizationUtil.kt`
```kotlin
package com.rusertech.mobile.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationUtil {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (!isIgnoringBatteryOptimizations(context)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
```

### Archivo: `util/OemUtil.kt`
```kotlin
package com.rusertech.mobile.util

object OemUtil {
    fun getManufacturer(): String = android.os.Build.MANUFACTURER.lowercase()

    fun needsSpecialSetup(): Boolean =
        getManufacturer() in listOf("xiaomi", "samsung", "huawei", "oppo", "vivo", "realme", "oneplus")

    fun getSetupInstructions(): String? = when (getManufacturer()) {
        "xiaomi" -> """
            Para que el seguimiento funcione correctamente en tu Xiaomi:
            1. Configuración → Apps → Rusertech Mobile → Ahorro de batería → Sin restricciones
            2. En recientes, mantené presionada la app y tocá el candado
            3. Configuración → Apps → Permisos → Autostart → activá Rusertech Mobile
        """.trimIndent()
        "samsung" -> """
            Para que el seguimiento funcione correctamente en tu Samsung:
            1. Configuración → Cuidado del dispositivo → Batería → Límites de uso en segundo plano
            2. Asegurate de que Rusertech Mobile NO esté en "Apps en suspensión"
            3. Agregá Rusertech Mobile a "Apps que nunca se suspenden"
        """.trimIndent()
        "oppo", "realme" -> """
            Para que el seguimiento funcione correctamente:
            1. Configuración → Batería → Optimizar uso de batería
            2. Buscá Rusertech Mobile y seleccioná "No optimizar"
        """.trimIndent()
        else -> null
    }
}
```

### Archivo: `ui/common/PermissionHandler.kt`
```kotlin
package com.rusertech.mobile.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHandler {
    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        else true

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        else true

    fun foregroundPermissions() = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}
```

### VERIFICAR
- [ ] `IdentityValidator.isValid("123.456.789-00")` → true (CPF)
- [ ] `PlateValidator.isValid("AB-123-CD")` → true (Mercosur)
- [ ] `BatteryOptimizationUtil` compila
- [ ] `OemUtil.getSetupInstructions()` retorna texto para Xiaomi y Samsung

---

## SECCIÓN 13 — Capa de Servicio

Incluye los fixes críticos aplicados:
- ❌ Fix #1: serviceScope se recrea tras stop
- ⚠️ Fix #8: deduplicación de posición estática
- ⚠️ Fix #9: filtro de precisión >50m

La única diferencia es que `TrackingService` ahora pasa `identity` (que incluye `avlUserCode` y `apiKey`) a los repositorios.

### Archivo: `service/LocationManager.kt`
```kotlin
package com.rusertech.mobile.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManager @Inject constructor(
    private val fusedClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        const val INTERVAL_MOVING_MS = 10_000L
        const val INTERVAL_IDLE_MS = 60_000L
        const val SPEED_THRESHOLD_MS = 2.0f
        const val SMALLEST_DISPLACEMENT_M = 10f
        const val MAX_ACCURACY_METERS = 50f
    }

    private val _locations = MutableSharedFlow<Location>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val locations: SharedFlow<Location> = _locations.asSharedFlow()
    private var callback: LocationCallback? = null
    private var currentInterval = INTERVAL_MOVING_MS
    private var lastEmitted: Location? = null

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        if (callback != null) return
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (shouldEmit(loc)) {
                    lastEmitted = loc
                    _locations.tryEmit(loc)
                    adaptInterval(loc.speed)
                }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, currentInterval)
            .setMinUpdateDistanceMeters(SMALLEST_DISPLACEMENT_M)
            .setMaxUpdateDelayMillis(currentInterval * 2)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    fun stopUpdates() {
        callback?.let { fusedClient.removeLocationUpdates(it); callback = null }
        lastEmitted = null
    }

    private fun shouldEmit(loc: Location): Boolean {
        if (loc.accuracy <= 0f || loc.accuracy > MAX_ACCURACY_METERS) return false
        val last = lastEmitted ?: return true
        return !(last.distanceTo(loc) < SMALLEST_DISPLACEMENT_M && loc.speed < SPEED_THRESHOLD_MS)
    }

    @SuppressLint("MissingPermission")
    private fun adaptInterval(speed: Float) {
        val target = if (speed < SPEED_THRESHOLD_MS) INTERVAL_IDLE_MS else INTERVAL_MOVING_MS
        if (target != currentInterval) {
            currentInterval = target
            callback?.let { fusedClient.removeLocationUpdates(it) }
            callback = null
            startUpdates()
        }
    }
}
```

### Archivo: `service/TrackingService.kt`
```kotlin
package com.rusertech.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.rusertech.mobile.MainActivity
import com.rusertech.mobile.R
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.AuthEventBus
import com.rusertech.mobile.data.remote.sync.AttachmentSyncWorker
import com.rusertech.mobile.data.remote.sync.SyncWorker
import com.rusertech.mobile.data.repository.EventRepository
import com.rusertech.mobile.data.repository.LocationRepository
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.domain.model.LocationPoint
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.BatteryUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {
    @Inject lateinit var locationManager: LocationManager
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var authEventBus: AuthEventBus

    private var serviceScope: CoroutineScope = newScope()
    private var identity: UserIdentity? = null
    private var collectJob: Job? = null
    private var authWatchJob: Job? = null
    private var lastBatteryAlert = 0L
    private var vehicleStoppedSince = 0L
    private var wasMoving = false

    companion object {
        const val ACTION_START = "com.rusertech.mobile.ACTION_START"
        const val ACTION_STOP = "com.rusertech.mobile.ACTION_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val REVOKED_NOTIFICATION_ID = 1002
        private const val CREDENTIAL_WARNING_NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "rusertech_tracking_channel"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        private val _lastLocation = MutableStateFlow<Location?>(null)
        val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()
        // Sección 10.1: true cuando el backend respondió 403 (acceso revocado a propósito)
        private val _accessRevoked = MutableStateFlow(false)
        val accessRevoked: StateFlow<Boolean> = _accessRevoked.asStateFlow()
        // Sección 10.1: true cuando el backend respondió 401 (API Key mal formada, NO detiene tracking)
        private val _credentialWarning = MutableStateFlow(false)
        val credentialWarning: StateFlow<Boolean> = _credentialWarning.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { ACTION_START -> start(); ACTION_STOP -> stop() }
        return START_STICKY
    }

    private fun start() {
        if (!serviceScope.isActive) serviceScope = newScope()
        _accessRevoked.value = false
        _credentialWarning.value = false
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_notification_text_active)))

        // Sección 10.1: escucha revocación (403) y advertencia de credenciales (401)
        // durante toda la vida del servicio — son dos reacciones distintas.
        authWatchJob?.cancel()
        authWatchJob = serviceScope.launch {
            launch { authEventBus.accessRevoked.collect { onAccessRevoked() } }
            launch { authEventBus.credentialWarning.collect { onCredentialWarning() } }
        }

        collectJob?.cancel()
        collectJob = serviceScope.launch {
            identity = userPreferences.snapshot()
            if (identity == null) { stopSelf(); return@launch }
            userPreferences.setTracking(true)
            _isRunning.value = true
            locationManager.startUpdates()

            locationManager.locations.collect { location ->
                _lastLocation.value = location
                val id = identity ?: return@collect
                val point = LocationPoint(
                    latitude = location.latitude, longitude = location.longitude,
                    accuracy = location.accuracy, speed = location.speed,
                    heading = if (location.hasBearing()) location.bearing else 0f,
                    altitude = location.altitude,
                    battery = BatteryUtil.getLevel(this@TrackingService),
                    timestamp = System.currentTimeMillis()
                )
                locationRepository.saveLocation(id, point)
                updateNotification(point.speedKmh().toInt())
                checkAutoEvents(location, id)
            }
        }
        scheduleSyncWork()
    }

    /**
     * Sección 10.1 — 403: el operador desactivó el avl_user o revocó la API Key
     * a propósito. Detiene el tracking y deja una notificación explicando por qué,
     * en vez de simplemente morir en silencio (lo cual confundiría al conductor).
     */
    private fun onAccessRevoked() {
        _accessRevoked.value = true
        stop()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(REVOKED_NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Acceso desactivado")
            .setContentText("Tu operador desactivó el seguimiento. Contactalo si es un error.")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build())
    }

    /**
     * Sección 10.1 — 401: la API Key está mal formada, típicamente un error de
     * carga durante el registro. NO se detiene el tracking — los puntos se
     * siguen guardando en Room con el mismo mecanismo offline-first de siempre.
     * Solo se avisa para que alguien corrija la credencial.
     */
    private fun onCredentialWarning() {
        _credentialWarning.value = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(CREDENTIAL_WARNING_NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Revisá tus credenciales")
            .setContentText("La API Key no es válida. El tracking sigue activo y guardando localmente.")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build())
    }

    private suspend fun checkAutoEvents(location: Location, id: UserIdentity) {
        val now = System.currentTimeMillis()
        val battery = BatteryUtil.getLevel(this)
        if (battery in 0..15 && now - lastBatteryAlert > 30 * 60_000L) {
            lastBatteryAlert = now
            eventRepository.createEvent(EventType.LOW_BATTERY, id, location.latitude, location.longitude,
                metadata = mapOf("battery_level" to battery.toString()))
        }
        val isMoving = location.speed >= LocationManager.SPEED_THRESHOLD_MS
        when {
            !isMoving && wasMoving -> vehicleStoppedSince = now
            !isMoving && vehicleStoppedSince > 0 && now - vehicleStoppedSince > 5 * 60_000L -> {
                eventRepository.createEvent(EventType.VEHICLE_STOP, id, location.latitude, location.longitude,
                    metadata = mapOf("stop_duration_seconds" to ((now - vehicleStoppedSince) / 1000).toString()))
                vehicleStoppedSince = 0L
            }
        }
        wasMoving = isMoving
    }

    private fun stop() {
        locationManager.stopUpdates()
        _isRunning.value = false; _lastLocation.value = null
        serviceScope.launch { userPreferences.setTracking(false) }
        collectJob?.cancel(); authWatchJob?.cancel(); serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() {
        locationManager.stopUpdates()
        if (serviceScope.isActive) serviceScope.cancel()
        _isRunning.value = false; _lastLocation.value = null
        super.onDestroy()
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun scheduleSyncWork() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("rusertech_sync", ExistingPeriodicWorkPolicy.KEEP, request)

        // Sección 29: sube fotos de carga pendientes en el mismo ciclo, worker separado
        // porque multipart no comparte el pipeline JSON de HubRawPayload.
        val attachmentRequest = PeriodicWorkRequestBuilder<AttachmentSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "rusertech_attachment_sync", ExistingPeriodicWorkPolicy.KEEP, attachmentRequest
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_LOW)
            .apply { description = getString(R.string.service_channel_description); setShowBadge(false); enableLights(false); enableVibration(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val openPending = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPending = PendingIntent.getService(this, 1,
            Intent(this, TrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title)).setContentText(content)
            .setSmallIcon(R.drawable.ic_notification).setOngoing(true).setSilent(true)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_notification, getString(R.string.service_notification_action_stop), stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()
    }

    private fun updateNotification(speedKmh: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("${getString(R.string.service_notification_text_active)} · $speedKmh km/h"))
    }
}
```

### VERIFICAR
- [ ] El servicio compila con todos los @Inject
- [ ] `newScope()` se recrea tras stop (Fix #1)
- [ ] Filtro de accuracy >50m activo (Fix #9)
- [ ] Deduplicación estática activa (Fix #8)

---

## SECCIÓN 14 — Sincronización en Background y Recuperación

### Archivo: `data/remote/sync/SyncWorker.kt`
```kotlin
package com.rusertech.mobile.data.remote.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.repository.EventRepository
import com.rusertech.mobile.data.repository.LocationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val locationRepository: LocationRepository,
    private val eventRepository: EventRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, params) {
    companion object { private const val TAG = "SyncWorker" }

    override suspend fun doWork(): Result {
        val identity = userPreferences.snapshot() ?: return Result.failure()
        return try {
            val events = eventRepository.syncPending(identity)
            val locations = locationRepository.syncPending(identity)
            val total = events.getOrDefault(0) + locations.getOrDefault(0)
            Log.i(TAG, "Sync ok — $total items")
            if (events.isFailure || locations.isFailure) {
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            } else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
```

### Archivo: `service/BootReceiver.kt`
```kotlin
package com.rusertech.mobile.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rusertech.mobile.data.local.prefs.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var userPreferences: UserPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (userPreferences.isTrackingSnapshot() && userPreferences.snapshot() != null) {
                    context.startForegroundService(
                        Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START }
                    )
                    Log.i("BootReceiver", "Tracking auto-reanudado")
                }
            } catch (e: Exception) { Log.e("BootReceiver", "Fallo al reanudar", e) }
            finally { pendingResult.finish() }
        }
    }
}
```

### VERIFICAR
- [ ] SyncWorker pasa `identity` a los repositorios (necesario para API Key)
- [ ] BootReceiver restaura tracking tras reboot

---

## SECCIÓN 15 — Inyección de Dependencias (Hilt)

### Archivo: `di/AppModule.kt`
```kotlin
package com.rusertech.mobile.di

import android.content.Context
import android.net.ConnectivityManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideFusedLocationClient(@ApplicationContext ctx: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(ctx)

    @Provides @Singleton
    fun provideConnectivityManager(@ApplicationContext ctx: Context): ConnectivityManager =
        ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
}
```

### Archivo: `di/DatabaseModule.kt`
```kotlin
package com.rusertech.mobile.di

import android.content.Context
import androidx.room.Room
import com.rusertech.mobile.data.local.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "rusertech_db")
            .fallbackToDestructiveMigration().build()

    @Provides fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()
    @Provides fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()
    @Provides fun provideAttachmentDao(db: AppDatabase): AttachmentDao = db.attachmentDao()
}
```

### Archivo: `di/NetworkModule.kt`
```kotlin
package com.rusertech.mobile.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rusertech.mobile.BuildConfig
import com.rusertech.mobile.data.remote.api.AttachmentApi
import com.rusertech.mobile.data.remote.api.TrackingApi
import com.rusertech.mobile.data.remote.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Provides @Singleton
    fun provideOkHttp(authInterceptor: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(true)
        .addInterceptor(authInterceptor)  // Detecta 401/403 → AuthEventBus (Sección 10.1)
        .apply {
            // Fix #6: solo headers en debug, sin PII en body
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS })
            }
        }.build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_BASE_URL).client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()

    @Provides @Singleton
    fun provideTrackingApi(retrofit: Retrofit): TrackingApi = retrofit.create(TrackingApi::class.java)

    @Provides @Singleton
    fun provideAttachmentApi(retrofit: Retrofit): AttachmentApi = retrofit.create(AttachmentApi::class.java)
}
```

### VERIFICAR
- [ ] Hilt genera el grafo sin errores
- [ ] En release, no hay logging interceptor

---

## SECCIÓN 16 — UI: Pantalla de Registro

> **CAMBIO v1.1:** Ahora incluye campos para `avlUserCode` y `apiKey` (provistos por el operador desde el dashboard web). Estos campos se pueden pre-configurar y ocultar en builds de producción si la flota usa una sola API Key.

### Archivo: `ui/common/RusertechTextField.kt`
```kotlin
package com.rusertech.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusertech.mobile.ui.theme.*

@Composable
fun RusertechTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, placeholder: String, error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
                .background(SurfaceInput, RoundedCornerShape(10.dp))
                .border(0.5.dp, if (error != null) SOSRed else SurfaceBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Normal),
            cursorBrush = SolidColor(TechGlowCyan), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = TextMuted); inner() }
        )
        if (error != null) { Spacer(Modifier.height(4.dp)); Text(error, fontSize = 11.sp, color = SOSRed) }
    }
}
```

### Archivo: `ui/common/GradientButton.kt`
```kotlin
package com.rusertech.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusertech.mobile.ui.theme.*

@Composable
fun GradientButton(text: String, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp))
            .then(if (enabled && !loading) Modifier.background(techGlowGradient()) else Modifier.background(SurfaceCard))
            .clickable(enabled = enabled && !loading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = DeepSpaceTop, strokeWidth = 2.dp)
        else Text(text, fontSize = 16.sp, fontWeight = FontWeight.W500, color = if (enabled) IconOnGlow else TextMuted)
    }
}
```

### Archivo: `ui/registration/RegistrationViewModel.kt`
```kotlin
package com.rusertech.mobile.ui.registration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.util.IdentityValidator
import com.rusertech.mobile.util.PlateValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    var documentId by mutableStateOf(""); private set
    var plate by mutableStateOf(""); private set
    var avlUserCode by mutableStateOf(""); private set
    var apiKey by mutableStateOf(""); private set
    var documentError by mutableStateOf<String?>(null); private set
    var plateError by mutableStateOf<String?>(null); private set
    var isLoading by mutableStateOf(false); private set

    val isValid: Boolean get() = IdentityValidator.isValid(documentId) &&
        PlateValidator.isValid(plate) && avlUserCode.isNotBlank() && apiKey.isNotBlank()

    fun onDocumentChange(input: String) {
        documentId = input.take(20)
        documentError = if (documentId.isNotEmpty()) IdentityValidator.errorOrNull(documentId) else null
    }
    fun onPlateChange(input: String) {
        plate = input.uppercase().take(10)
        plateError = if (plate.isNotEmpty()) PlateValidator.errorOrNull(plate) else null
    }
    fun onAvlCodeChange(input: String) { avlUserCode = input.trim().take(50) }
    fun onApiKeyChange(input: String) { apiKey = input.trim().take(200) }

    fun save(onDone: () -> Unit) {
        documentError = IdentityValidator.errorOrNull(documentId)
        plateError = PlateValidator.errorOrNull(plate)
        if (documentError != null || plateError != null) return
        viewModelScope.launch {
            isLoading = true
            userRepository.saveIdentity(
                IdentityValidator.normalize(documentId), PlateValidator.normalize(plate),
                avlUserCode, apiKey
            )
            isLoading = false
            onDone()
        }
    }
}
```

### Archivo: `ui/registration/RegistrationScreen.kt`
```kotlin
package com.rusertech.mobile.ui.registration

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rusertech.mobile.R
import com.rusertech.mobile.ui.common.GradientButton
import com.rusertech.mobile.ui.common.RusertechTextField
import com.rusertech.mobile.ui.theme.*

@Composable
fun RegistrationScreen(
    onRegistered: () -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.fillMaxSize().background(deepSpaceGradient())
            .padding(24.dp).systemBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Box(modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(techGlowGradient()),
            contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.rusertech_logo), contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.app_name), fontSize = 24.sp, fontWeight = FontWeight.W500, color = TextPrimary)
        Text(stringResource(R.string.app_tagline), fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(28.dp))

        // Campos del conductor
        RusertechTextField(viewModel.documentId, viewModel::onDocumentChange,
            stringResource(R.string.register_dni_label), stringResource(R.string.register_dni_placeholder),
            error = viewModel.documentError, keyboardType = KeyboardType.Text)
        Spacer(Modifier.height(12.dp))
        RusertechTextField(viewModel.plate, viewModel::onPlateChange,
            stringResource(R.string.register_plate_label), stringResource(R.string.register_plate_placeholder),
            error = viewModel.plateError, capitalization = KeyboardCapitalization.Characters)

        Spacer(Modifier.height(20.dp))
        Text("Configuración de flota", fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextSecondary)
        Text("Estos datos los provee el operador desde el panel web", fontSize = 11.sp, color = TextMuted)
        Spacer(Modifier.height(8.dp))

        // Campos de configuración de flota (provistos por el operador)
        RusertechTextField(viewModel.avlUserCode, viewModel::onAvlCodeChange,
            "Código AVL", "Código del dispositivo mobile")
        Spacer(Modifier.height(12.dp))
        RusertechTextField(viewModel.apiKey, viewModel::onApiKeyChange,
            "API Key", "Clave de autenticación")

        Spacer(Modifier.height(28.dp))
        GradientButton(stringResource(R.string.register_save), viewModel.isValid, viewModel.isLoading) {
            viewModel.save(onRegistered)
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.register_disclaimer), fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center)
    }
}
```

### VERIFICAR
- [ ] El logo renderiza dentro del cuadrado con gradiente
- [ ] Los 4 campos son visibles y funcionales
- [ ] El botón se deshabilita si falta algún campo

---

## SECCIÓN 17 — UI: Pantalla de Tracking

### Archivo: `ui/tracking/TrackingViewModel.kt`
```kotlin
package com.rusertech.mobile.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.repository.LocationRepository
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.service.TrackingService
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val locationRepository: LocationRepository,
    private val networkUtil: NetworkUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val userIdentity = userRepository.userIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val isTracking = TrackingService.isRunning
    val lastLocation = TrackingService.lastLocation
    val accessRevoked = TrackingService.accessRevoked  // Sección 10.1 — 403
    val credentialWarning = TrackingService.credentialWarning  // Sección 10.1 — 401
    val isOnline = networkUtil.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pendingCount = locationRepository.getUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startTracking() {
        context.startForegroundService(
            Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START }
        )
    }

    // Fix #5: stopService en vez de startService
    fun stopTracking() {
        context.startService(
            Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_STOP }
        )
        context.stopService(Intent(context, TrackingService::class.java))
    }

    fun logout() {
        stopTracking()
        viewModelScope.launch { userRepository.logout() }
    }
}
```

### Archivo: `ui/tracking/TrackingScreen.kt`
```kotlin
package com.rusertech.mobile.ui.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rusertech.mobile.R
import com.rusertech.mobile.ui.theme.*
import com.rusertech.mobile.util.BatteryUtil

@Composable
fun TrackingScreen(
    onLogout: () -> Unit, onNavigateToEvents: () -> Unit,
    onNavigateToAttachments: () -> Unit,  // Sección 29
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val identity by viewModel.userIdentity.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val lastLocation by viewModel.lastLocation.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val accessRevoked by viewModel.accessRevoked.collectAsStateWithLifecycle()
    val credentialWarning by viewModel.credentialWarning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val battery = remember { BatteryUtil.getLevel(context) }

    Column(
        modifier = Modifier.fillMaxSize().background(deepSpaceGradient()).padding(20.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sección 10.1 — 403: banner rojo, tracking detenido, botón bloqueado
        if (accessRevoked) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = SOSRed.copy(alpha = 0.15f)
            ) {
                Text(
                    "Tu acceso fue desactivado por el operador. Contactalo si es un error.",
                    modifier = Modifier.padding(12.dp),
                    color = SOSRed, fontSize = 12.sp
                )
            }
        }
        // Sección 10.1 — 401: banner ámbar, tracking SIGUE activo, solo advierte
        if (credentialWarning && !accessRevoked) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = WarningAmber.copy(alpha = 0.15f)
            ) {
                Text(
                    "Tu API Key no es válida. El tracking sigue activo y guardando localmente — pedile al operador que la revise.",
                    modifier = Modifier.padding(12.dp),
                    color = WarningAmber, fontSize = 12.sp
                )
            }
        }
        // Header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), fontSize = 17.sp, fontWeight = FontWeight.W500, color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(if (isTracking) stringResource(R.string.tracking_active) else stringResource(R.string.tracking_stopped),
                    if (isTracking) SuccessGreen else TextMuted)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, "Salir", tint = TextSecondary) }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Identidad
        identity?.let {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IdentityChip("Documento", it.documentId, Modifier.weight(1f))
                IdentityChip("Patente", it.plate, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        // Velocímetro
        Box(modifier = Modifier.size(140.dp).border(3.dp,
            if (isTracking) techGlowGradient() else Brush.linearGradient(listOf(SurfaceBorder, SurfaceBorder)), CircleShape),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val kmh = lastLocation?.let { (it.speed * 3.6f).toInt() } ?: 0
                Text("$kmh", fontSize = 40.sp, fontWeight = FontWeight.W500, color = TextPrimary)
                Text("km/h", fontSize = 13.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(20.dp))
        // Card de estado
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp), border = BorderStroke(0.5.dp, SurfaceBorder)) {
            Column(Modifier.padding(14.dp)) {
                StatusRow("GPS", lastLocation != null, "Precisión ${lastLocation?.accuracy?.toInt() ?: 0}m", "Buscando señal")
                StatusRow("Red", isOnline, "Conectado", "Sin conexión")
                StatusRow("Tracking", isTracking, "Activo", "Detenido")
                StatusRow("Batería", battery > 20, "$battery%", "$battery% (baja)")
                if (pendingCount > 0) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween) {
                        Text("Pendientes", fontSize = 13.sp, color = TextSecondary)
                        Text("$pendingCount", fontSize = 13.sp, color = WarningAmber)
                    }
                }
            }
        }
        lastLocation?.let { loc ->
            Spacer(Modifier.height(8.dp))
            Text("${"%.6f".format(loc.latitude)}, ${"%.6f".format(loc.longitude)}",
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted)
        }
        Spacer(Modifier.weight(1f))
        // Botón eventos
        OutlinedButton(onClick = onNavigateToEvents, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SurfaceBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)) {
            Icon(Icons.Default.Notifications, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.events_title), fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        // Botón fotos de carga (Sección 29)
        OutlinedButton(onClick = onNavigateToAttachments, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SurfaceBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)) {
            Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Fotos de carga", fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        // Botón principal — bloqueado si el operador revocó el acceso (Sección 10.1, 403)
        Box(modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    accessRevoked -> SurfaceCard
                    !isTracking -> techGlowGradient()
                    else -> Brush.horizontalGradient(listOf(SOSRed, SOSRed))
                }
            )
            .clickable(enabled = !accessRevoked) {
                if (isTracking) viewModel.stopTracking() else viewModel.startTracking()
            },
            contentAlignment = Alignment.Center) {
            Text(
                when {
                    accessRevoked -> "Acceso desactivado"
                    isTracking -> stringResource(R.string.tracking_stop)
                    else -> stringResource(R.string.tracking_start)
                },
                fontSize = 17.sp, fontWeight = FontWeight.W500,
                color = if (accessRevoked) TextMuted else if (!isTracking) IconOnGlow else Color.White
            )
        }
    }
}

@Composable private fun IdentityChip(label: String, value: String, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(8.dp), color = SurfaceInput) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.W500, color = TextPrimary)
        }
    }
}

@Composable private fun StatusRow(label: String, ok: Boolean, okText: String, badText: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (ok) SuccessGreen else SOSRed, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (ok) okText else badText, fontSize = 13.sp, color = if (ok) SuccessGreen else SOSRed)
        }
    }
}

@Composable private fun StatusBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.15f)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, color = color, fontWeight = FontWeight.W500)
    }
}
```

### VERIFICAR
- [ ] Anillo de velocidad con gradiente Tech Glow cuando trackea
- [ ] `stopTracking()` no crashea si el servicio ya murió (Fix #5)

---

## SECCIÓN 18 — UI: Pantalla de Eventos

### Archivo: `ui/events/EventsViewModel.kt`
```kotlin
package com.rusertech.mobile.ui.events

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.db.EventEntity
import com.rusertech.mobile.data.repository.EventRepository
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.service.TrackingService
import com.rusertech.mobile.util.BatteryUtil
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val networkUtil: NetworkUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val userIdentity: StateFlow<UserIdentity?> = userRepository.userIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val isOnline: StateFlow<Boolean> = networkUtil.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()
    val recentEvents: StateFlow<List<EventEntity>> = eventRepository.getRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingCount: StateFlow<Int> = eventRepository.getUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun fireEvent(type: EventType) {
        viewModelScope.launch {
            val identity = userIdentity.value ?: return@launch
            val location = TrackingService.lastLocation.value
            eventRepository.createEvent(type, identity, location?.latitude ?: 0.0, location?.longitude ?: 0.0)
            _feedback.value = type.displayName; delay(3000); _feedback.value = null
        }
    }

    fun fireSOS() {
        triggerVibration()
        viewModelScope.launch {
            val identity = userIdentity.value ?: return@launch
            val location = TrackingService.lastLocation.value
            eventRepository.createEvent(EventType.SOS, identity, location?.latitude ?: 0.0, location?.longitude ?: 0.0,
                metadata = mapOf("battery" to BatteryUtil.getLevel(context).toString(),
                    "network" to if (isOnline.value) "online" else "offline"))
            _feedback.value = "SOS enviado"; delay(3000); _feedback.value = null
        }
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }
}
```

### Archivo: `ui/events/EventsScreen.kt`
```kotlin
package com.rusertech.mobile.ui.events

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rusertech.mobile.R
import com.rusertech.mobile.data.local.db.EventEntity
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EventsScreen(onBack: () -> Unit, viewModel: EventsViewModel = hiltViewModel()) {
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val recentEvents by viewModel.recentEvents.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(deepSpaceGradient()).padding(20.dp).systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        Row(Modifier.fillMaxWidth(), Alignment.CenterVertically, Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary) }
                Text(stringResource(R.string.events_title), fontSize = 20.sp, fontWeight = FontWeight.W500, color = TextPrimary)
            }
            Surface(RoundedCornerShape(20.dp), color = (if (isOnline) SuccessGreen else WarningAmber).copy(alpha = 0.15f)) {
                Text(if (isOnline) "En línea" else "Offline", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 11.sp, color = if (isOnline) SuccessGreen else WarningAmber, fontWeight = FontWeight.W500)
            }
        }
        // SOS
        Button(onClick = { viewModel.fireSOS() }, Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = SOSRed)) {
            Text(feedback?.takeIf { it.contains("SOS") } ?: stringResource(R.string.events_sos),
                fontSize = 17.sp, fontWeight = FontWeight.W500, color = Color.White)
        }
        // Acciones rápidas
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(Icons.Default.Phone, stringResource(R.string.events_communication), InfoBlue, Modifier.weight(1f)) {
                viewModel.fireEvent(EventType.COMMUNICATION_REQUEST) }
            QuickAction(Icons.Default.LocationOn, stringResource(R.string.events_checkpoint), SuccessGreen, Modifier.weight(1f)) {
                viewModel.fireEvent(EventType.CHECKPOINT) }
            QuickAction(Icons.Default.Warning, stringResource(R.string.events_incident), WarningAmber, Modifier.weight(1f)) {
                viewModel.fireEvent(EventType.INCIDENT) }
        }
        // Feedback
        AnimatedVisibility(feedback != null && !feedback!!.contains("SOS"), enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), color = SuccessGreen.copy(alpha = 0.15f)) {
                Text(feedback ?: "", Modifier.fillMaxWidth().padding(12.dp), SuccessGreen, 14.sp, textAlign = TextAlign.Center)
            }
        }
        if (pendingCount > 0) Text("$pendingCount eventos pendientes", fontSize = 12.sp, color = WarningAmber)
        // Historial
        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp), border = BorderStroke(0.5.dp, SurfaceBorder)) {
            Column(Modifier.padding(14.dp)) {
                Text(stringResource(R.string.events_recent_history), fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                if (recentEvents.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
                        Text(stringResource(R.string.events_no_events), fontSize = 14.sp, color = TextMuted)
                    }
                } else {
                    LazyColumn { items(recentEvents, key = { it.id }) { event ->
                        EventRow(event)
                        if (event != recentEvents.last()) HorizontalDivider(thickness = 0.5.dp, color = SurfaceBorder)
                    } }
                }
            }
        }
    }
}

@Composable private fun QuickAction(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick, modifier, shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SurfaceBorder),
        colors = CardDefaults.outlinedCardColors(containerColor = SurfaceCard)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            Alignment.CenterHorizontally, Arrangement.spacedBy(6.dp)) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 11.sp, color = TextPrimary, textAlign = TextAlign.Center, lineHeight = 14.sp)
        }
    }
}

@Composable private fun EventRow(event: EventEntity) {
    val dotColor = when (event.type) {
        "MOB_SOS" -> SOSRed; "MOB_COMM" -> InfoBlue; "MOB_CHKPT" -> SuccessGreen
        "MOB_INCIDENT" -> WarningAmber; "MOB_LOWBAT" -> WarningAmber; "MOB_STOP" -> TextMuted
        else -> TextSecondary
    }
    val timeStr = remember(event.timestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.CenterVertically, Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(EventType.fromCode(event.type)?.displayName ?: event.type, fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextPrimary)
            Text("$timeStr · ${"%.4f".format(event.latitude)}, ${"%.4f".format(event.longitude)}", fontSize = 11.sp, color = TextMuted)
        }
        if (!event.isSynced) Box(Modifier.size(6.dp).background(WarningAmber, CircleShape))
    }
}
```

### VERIFICAR
- [ ] SOS dispara vibración háptica y muestra "SOS enviado"
- [ ] Los eventos aparecen en el historial con dot de color
- [ ] Eventos no sincronizados muestran dot ámbar

---

## SECCIÓN 19 — Navegación

### Archivo: `ui/splash/SplashRoute.kt`
```kotlin
package com.rusertech.mobile.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.rusertech.mobile.ui.theme.deepSpaceGradient
import com.rusertech.mobile.ui.tracking.TrackingViewModel

@Composable
fun SplashRoute(onRegistered: () -> Unit, onNeedsRegistration: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()) {
    val identity by viewModel.userIdentity.collectAsState()
    LaunchedEffect(identity) {
        if (identity != null) { onRegistered() }
        else { kotlinx.coroutines.delay(300); if (viewModel.userIdentity.value == null) onNeedsRegistration() }
    }
    Box(Modifier.fillMaxSize().background(deepSpaceGradient()))
}
```

### Archivo: `ui/navigation/NavGraph.kt`
```kotlin
package com.rusertech.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rusertech.mobile.ui.attachments.AttachmentsScreen
import com.rusertech.mobile.ui.events.EventsScreen
import com.rusertech.mobile.ui.registration.RegistrationScreen
import com.rusertech.mobile.ui.splash.SplashRoute
import com.rusertech.mobile.ui.tracking.TrackingScreen

@Composable
fun RusertechNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = "splash") {
        composable("splash") {
            SplashRoute(
                onRegistered = { navController.navigate("tracking") { popUpTo("splash") { inclusive = true } } },
                onNeedsRegistration = { navController.navigate("registration") { popUpTo("splash") { inclusive = true } } }
            )
        }
        composable("registration") {
            RegistrationScreen(onRegistered = {
                navController.navigate("tracking") { popUpTo("registration") { inclusive = true } }
            })
        }
        composable("tracking") {
            TrackingScreen(
                onLogout = { navController.navigate("registration") { popUpTo("tracking") { inclusive = true } } },
                onNavigateToEvents = { navController.navigate("events") },
                onNavigateToAttachments = { navController.navigate("attachments") }  // Sección 29
            )
        }
        composable("events") { EventsScreen(onBack = { navController.popBackStack() }) }
        composable("attachments") { AttachmentsScreen(onBack = { navController.popBackStack() }) }  // Sección 29
    }
}
```

### VERIFICAR
- [ ] splash → registro (primer uso) o tracking (usuario que vuelve)
- [ ] Logout limpia backstack

---

## SECCIÓN 20 — Application y MainActivity

### Archivo: `RusertechApp.kt`
```kotlin
package com.rusertech.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RusertechApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO).build()
}
```

### Archivo: `MainActivity.kt`
```kotlin
package com.rusertech.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rusertech.mobile.ui.navigation.RusertechNavHost
import com.rusertech.mobile.ui.theme.DeepSpaceTop
import com.rusertech.mobile.ui.theme.RusertechTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RusertechTheme { Surface(Modifier.fillMaxSize(), color = DeepSpaceTop) { RusertechNavHost() } } }
    }
}
```

### VERIFICAR
- [ ] `@HiltAndroidApp` en Application
- [ ] Sin flash blanco al abrir

---

## SECCIÓN 21 — Flujo de Permisos

Flujo de 3 pasos implementado en TrackingScreen:

```
Paso 1: ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION → juntos
Paso 2 (API ≥ 29): ACCESS_BACKGROUND_LOCATION → separado, con diálogo previo
Paso 3 (API ≥ 33): POST_NOTIFICATIONS → separado
```

El helper `PermissionHandler.kt` está completo en la Sección 12. Se invoca antes de `startTracking()`.

### VERIFICAR
- [ ] Android 10: background location se pide por separado
- [ ] Android 13: notificación se pide
- [ ] Android 8: solo foreground location

---

## SECCIÓN 22 — Testing

### Archivo: `test/com/rusertech/mobile/util/IdentityValidatorTest.kt`
```kotlin
package com.rusertech.mobile.util

import org.junit.Assert.*
import org.junit.Test

class IdentityValidatorTest {
    @Test fun `AR DNI 8 dígitos es válido`() { assertTrue(IdentityValidator.isValid("30456789")) }
    @Test fun `AR DNI 7 dígitos es válido`() { assertTrue(IdentityValidator.isValid("5456789")) }
    @Test fun `BR CPF con separadores normaliza y es válido`() {
        assertTrue(IdentityValidator.isValid("123.456.789-00"))
        assertEquals("12345678900", IdentityValidator.normalize("123.456.789-00"))
    }
    @Test fun `CL RUT con K es válido`() {
        assertTrue(IdentityValidator.isValid("12345678-K"))
        assertEquals("12345678K", IdentityValidator.normalize("12345678-K"))
    }
    @Test fun `MX CURP 18 chars es válido`() { assertTrue(IdentityValidator.isValid("ABCD123456HABCDE01")) }
    @Test fun `muy corto es inválido`() { assertFalse(IdentityValidator.isValid("123")) }
    @Test fun `vacío es inválido`() { assertFalse(IdentityValidator.isValid("")) }
    @Test fun `más de 20 chars es inválido`() { assertFalse(IdentityValidator.isValid("A".repeat(21))) }
}
```

### Archivo: `test/com/rusertech/mobile/util/PlateValidatorTest.kt`
```kotlin
package com.rusertech.mobile.util

import org.junit.Assert.*
import org.junit.Test

class PlateValidatorTest {
    @Test fun `Mercosur AB123CD`() { assertTrue(PlateValidator.isValid("AB123CD")) }
    @Test fun `AR clásica ABC123`() { assertTrue(PlateValidator.isValid("ABC123")) }
    @Test fun `BR viejo ABC1234`() { assertTrue(PlateValidator.isValid("ABC1234")) }
    @Test fun `Chile ABCD12`() { assertTrue(PlateValidator.isValid("ABCD12")) }
    @Test fun `con guiones normaliza`() {
        assertTrue(PlateValidator.isValid("AB-123-CD"))
        assertEquals("AB123CD", PlateValidator.normalize("AB-123-CD"))
    }
    @Test fun `minúsculas se normalizan`() { assertEquals("AB123CD", PlateValidator.normalize("ab123cd")) }
    @Test fun `muy corto es inválido`() { assertFalse(PlateValidator.isValid("AB12")) }
    @Test fun `muy largo es inválido`() { assertFalse(PlateValidator.isValid("ABCDEFGHI")) }
}
```

### Archivo: `androidTest/com/rusertech/mobile/data/local/db/LocationDaoTest.kt`
```kotlin
package com.rusertech.mobile.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocationDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LocationDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.locationDao()
    }
    @After fun teardown() { db.close() }

    @Test fun insertAndRetrieveUnsynced() = runTest {
        dao.insert(LocationEntity(latitude = -34.603, longitude = -58.381, accuracy = 10f,
            speed = 5f, heading = 180f, altitude = 25.0, battery = 80, timestamp = System.currentTimeMillis()))
        val unsynced = dao.getUnsynced()
        assertEquals(1, unsynced.size)
    }

    @Test fun markSyncedExcludesFromUnsynced() = runTest {
        val id = dao.insert(LocationEntity(latitude = 0.0, longitude = 0.0, accuracy = 10f,
            speed = 0f, heading = 0f, altitude = 0.0, battery = 100, timestamp = System.currentTimeMillis()))
        dao.markSynced(listOf(id))
        assertTrue(dao.getUnsynced().isEmpty())
    }

    @Test fun unsyncedCountFlow() = runTest {
        dao.insert(LocationEntity(latitude = 0.0, longitude = 0.0, accuracy = 10f,
            speed = 0f, heading = 0f, altitude = 0.0, battery = 100, timestamp = System.currentTimeMillis()))
        assertEquals(1, dao.getUnsyncedCount().first())
    }
}
```

### VERIFICAR
- [ ] `./gradlew test` pasa todos los unitarios
- [ ] Formatos LATAM validan correctamente

---

## SECCIÓN 23 — Contrato del Backend (Integración con Rusertech Web)

### Endpoint único

| Método | Ruta | Header | Body | Respuesta |
|--------|------|--------|------|-----------|
| POST | `/api/v1/telemetry/ingest` | `X-Hub-Api-Key: {apiKey}` | `HubRawPayload` | 200 OK |
| POST | `/api/v1/telemetry/ingest/batch` | `X-Hub-Api-Key: {apiKey}` | `HubRawPayload[]` | 200 OK |

### Ejemplo de payload — telemetría normal (sin evento)
```json
{
  "Asset": "AB123CD",
  "User_avl": "mobile_fleet_01",
  "Date": "2026-05-04T15:30:00.000Z",
  "Latitude": "-34.603722",
  "Longitude": "-58.381592",
  "Speed": "45.2",
  "Course": "180",
  "Code": null,
  "Ignition": null,
  "Altitude": "25.0",
  "Battery": "72",
  "Temperature": null,
  "Humidity": null,
  "Direction": "S",
  "SerialNumber": null,
  "Shipment": null,
  "SourceTag": "mobile_app",
  "Alert": null
}
```

### Ejemplo de payload — evento SOS
```json
{
  "Asset": "AB123CD",
  "User_avl": "mobile_fleet_01",
  "Date": "2026-05-04T15:32:00.000Z",
  "Latitude": "-34.603722",
  "Longitude": "-58.381592",
  "Speed": "0",
  "Course": null,
  "Code": "MOB_SOS",
  "Battery": "42",
  "Shipment": "Conductor solicita ayuda inmediata",
  "SourceTag": "mobile_app",
  "Alert": null
}
```

### Códigos de evento mobile (diccionario del avl_user)

El operador debe configurar estos códigos en el diccionario del avl_user mobile dentro del dashboard web:

| Código Mobile | Evento Estándar Rusertech | Severidad | Acción Sugerida |
|--------------|--------------------------|-----------|-----------------|
| `MOB_SOS` | `sos_alert` | critical | email + push + whatsapp |
| `MOB_COMM` | `communication_request` | info | push al operador |
| `MOB_CHKPT` | `checkpoint_reached` | info | log |
| `MOB_INCIDENT` | `incident_report` | warning | email + push |
| `MOB_STOP` | `unauthorized_stop` | warning | push |
| `MOB_LOWBAT` | `battery_low` | warning | push |

### Manejo de errores
- `2xx` → marcar como sincronizado
- `401` → API Key inválida. Mostrar error al usuario.
- `4xx` → NO reintentar (dato inválido)
- `5xx` → reintentar con backoff exponencial (hasta 5 intentos)

### Idempotencia
El backend deduplica por `(vehicleId, timestamp)` vía Redis. Los reintentos no generan duplicados.

### VERIFICAR
- [ ] El backend acepta el payload con `SourceTag: "mobile_app"`
- [ ] Los códigos `MOB_*` están configurados en el diccionario del avl_user
- [ ] El dashboard web muestra los datos de la mobile en tiempo real

---

## SECCIÓN 24 — Build y Release

### Build debug
```bash
./gradlew assembleDebug
```

### Build release
```bash
keytool -genkey -v -keystore rusertech-release.jks -alias rusertech -keyalg RSA -keysize 2048 -validity 10000

export KEYSTORE_PASSWORD=tu_password
export KEY_PASSWORD=tu_password
./gradlew assembleRelease
./gradlew bundleRelease  # AAB para Play Store
```

### VERIFICAR
- [ ] APK firmado se instala en dispositivo físico
- [ ] Tracking funciona 30+ minutos continuos

---

## SECCIÓN 25 — Checklist de Producción

**Crítico**
- [ ] `BACKEND_BASE_URL` apunta a producción
- [ ] API Key del avl_user mobile configurada en el dashboard web
- [ ] Diccionario de códigos `MOB_*` cargado en el avl_user
- [ ] Keystore de release creado y en CI
- [ ] Testear en Samsung, Xiaomi y Huawei
- [ ] Testear permisos en Android 10, 12, 13, 14 y 16 (edge-to-edge por defecto — ver Sección 31)
- [ ] Verificar que tracking sobrevive turno de 8 horas

**Importante**
- [ ] Agregar crash reporting (Sentry/Crashlytics)
- [ ] Testear offline: modo avión 30 min → verificar sync al volver
- [ ] Consumo de batería < 5% por hora
- [ ] Reboot recovery: tracking activo → reiniciar → verificar auto-reanudación

---

## SECCIÓN 26 — Compatibilidad OEM y Optimización de Batería

### 26.1 — Problemas reales por fabricante

**Xiaomi (MIUI/HyperOS):** Battery Killer mata procesos en background después de 15-20 min. Requiere: autostart habilitado, sin restricciones de batería, app bloqueada en recientes.

**Samsung (OneUI):** Sleeping Apps/Deep Sleeping restringe ejecución en background. Adaptive Battery throttlea Foreground Service después de horas. Modelos A-series son más agresivos que S-series.

**Dispositivos low-cost (Alcatel, ZTE, genéricos):** Comportamiento impredecible, RAM limitada (2-3 GB), GPS de baja calidad.

### 26.2 — Limitaciones reales de Android

- Foreground Service NO garantiza ejecución infinita
- `START_STICKY` re-agenda reinicio pero puede tardar minutos
- Doze Mode puede degradar frecuencia de ubicación
- FusedLocationProvider sigue entregando en Doze si el service tiene tipo `location`

### 26.3 — Tabla de estrategias

| # | Estrategia | Prioridad | Estado |
|---|-----------|-----------|--------|
| 1 | Foreground Service + START_STICKY | ❌ Obligatorio | ✅ Implementado |
| 2 | Scope recreado tras kill | ❌ Obligatorio | ✅ Implementado |
| 3 | BootReceiver | ❌ Obligatorio | ✅ Implementado |
| 4 | Exención de optimización de batería | ❌ Obligatorio | ✅ BatteryOptimizationUtil |
| 5 | Guía OEM-específica | ⚠️ Recomendado | ✅ OemUtil |
| 6 | WorkManager como backup | ⚠️ Recomendado | ✅ Implementado |
| 7 | AlarmManager heartbeat | ✅ Opcional | 📋 v1.2 |
| 8 | Integración dontkillmyapp.com | ✅ Opcional | 📋 v1.2 |

### 26.4 — Estrategia UX

Mostrar instrucciones OEM una sola vez, al primer "Iniciar seguimiento", después de permisos. No bloquear. Guardar en DataStore que ya se mostró.

### VERIFICAR
- [ ] En Xiaomi Redmi: tracking sobrevive 30+ min con pantalla apagada
- [ ] El diálogo de exención de batería aparece una sola vez

---

## SECCIÓN 27 — Soporte iOS (Fase Futura)

**Aclaración:** Esta arquitectura es Android-first. iOS no está incluido.

### Evaluación
- iOS es viable pero con limitaciones: no hay Foreground Service, no hay BootReceiver, no hay reinicio automático
- Background location se throttlea a 1-5 min
- Si el usuario cierra la app, el tracking muere
- App Store es estricto con apps que piden "Always" location

### Recomendación
- Swift nativo + SwiftUI (no Flutter, no KMP para v1)
- CLLocationManager con `allowsBackgroundLocationUpdates`
- Mismo contrato de API (HubRawPayload hacia el mismo endpoint)
- Requiere su propio documento de implementación — no es un port de Android

---

## SECCIÓN 28 — Integración con Rusertech Web (Opción A: Mobile como HUB)

### Decisión arquitectónica

La app mobile **no tiene backend propio**. Actúa como un HUB GPS más dentro del pipeline existente de Rusertech Web. Esta decisión se tomó porque:

1. **Cero código nuevo en backend** — el endpoint de ingesta ya existe y procesa cualquier HUB
2. **Pipeline unificado** — las alertas, el scoring de riesgo, Socket.io, y el dashboard web funcionan automáticamente
3. **RLS funciona** — la data se filtra por tenant como cualquier otro dato
4. **Deduplicación integrada** — Redis ya maneja duplicados por `(vehicleId, timestamp)`

### Setup necesario en el dashboard web

```
PASO 1 — El operador crea un avl_user de tipo mobile
  Dashboard → AVL Users → Nuevo
  Nombre: "Flota Mobile [nombre empresa]"
  Tipo: mobile_app
  → Se genera automáticamente una API Key

PASO 2 — Registrar los vehículos
  Dashboard → Flota → cada vehículo debe tener:
  - plate: la patente (coincide con el campo Asset del HUB)
  - avl_user_id: asociado al avl_user mobile creado en paso 1

PASO 3 — Configurar el diccionario de eventos
  Dashboard → AVL Users → [mobile] → Diccionario
  Agregar los códigos:
  MOB_SOS       → sos_alert (critical)
  MOB_COMM      → communication_request (info)
  MOB_CHKPT     → checkpoint_reached (info)
  MOB_INCIDENT  → incident_report (warning)
  MOB_STOP      → unauthorized_stop (warning)
  MOB_LOWBAT    → battery_low (warning)

PASO 4 — Proveer datos al conductor
  El conductor necesita 4 datos para registrarse en la app:
  1. Su documento de identidad
  2. La patente del vehículo (debe coincidir con vehicles.plate)
  3. El código del avl_user (User_avl)
  4. La API Key
```

### Flujo de datos completo

```
App Mobile
  ↓ POST /api/v1/telemetry/ingest (X-Hub-Api-Key)
  ↓ Body: HubRawPayload { Asset=plate, User_avl=code, Code=MOB_SOS|null, ... }
  ↓
Backend NestJS (TelemetryIngestion)
  ↓ Valida API Key → resuelve avl_user_id
  ↓ Asset + avl_user_id → resuelve vehicle_id
  ↓ Deduplicación Redis
  ↓ INSERT telemetry + INSERT outbox (1 transacción)
  ↓ UPDATE Redis: última posición
  ↓
BullMQ: telemetry.raw
  ↓
EventEngine
  ↓ Evalúa reglas del tenant + diccionario del avl_user
  ↓ Code "MOB_SOS" → sos_alert (critical) → INSERT event_logs
  ↓ Dispara notificaciones (email, push, whatsapp)
  ↓
Socket.io → Dashboard Web
  ↓ El operador ve la posición y eventos en tiempo real
```

### Mapeo de campos Mobile → Backend

| Campo Mobile (Kotlin) | Campo HUB Payload | Campo en tabla `telemetry` |
|----------------------|-------------------|---------------------------|
| `plate` | `Asset` | → resuelve `vehicle_id` |
| `avlUserCode` | `User_avl` | → resuelve `avl_user_id` |
| `timestamp` (ISO) | `Date` | `timestamp` |
| `latitude` | `Latitude` | `latitude` |
| `longitude` | `Longitude` | `longitude` |
| `speed * 3.6` (km/h) | `Speed` | `speed_kmh` |
| `heading` | `Course` | `heading_degrees` |
| `battery` | `Battery` | `battery_pct` |
| `altitude` | `Altitude` | `altitude_meters` |
| `EventType.code` | `Code` | `event_type` (vía diccionario) |
| `"mobile_app"` | `SourceTag` | guardado en `raw_payload` |

### Tabla `drivers` — vínculo del documento

El campo `documentId` que el conductor ingresa en la app se mapea a `drivers.document` en la tabla de conductores del backend. El operador debe asignar el conductor al vehículo en el dashboard web (`trips.driver_id`). La app no escribe directamente en la tabla `drivers` — solo envía telemetría con la patente como identificador del vehículo.

### VERIFICAR
- [ ] El avl_user mobile está creado en el dashboard web
- [ ] Los vehículos tienen `avl_user_id` apuntando al avl_user mobile
- [ ] El diccionario de códigos `MOB_*` está cargado
- [ ] Un POST desde la app aparece en el dashboard web en tiempo real
- [ ] Un evento MOB_SOS dispara la alerta configurada

---

## SECCIÓN 29 — Captura de Fotos de Carga

> **⚠️ EXCEPCIÓN A LA REGLA "CERO BACKEND NUEVO":** A diferencia de la telemetría (Sección 28), esta funcionalidad **sí requiere trabajo nuevo en el backend web** — una tabla, un bucket de Storage y un endpoint. El pipeline de ingesta de HUBs no tiene forma de transportar binarios. Esto se documenta explícitamente acá para que no se asuma que "ya está" del lado web.

### 29.1 — Qué resuelve

El conductor fotografía la carga en momentos clave del viaje (inicio, fin, incidente) y esas fotos quedan visibles en el dashboard, vinculadas al vehículo/viaje, con georreferencia y timestamp. Sirve para: validar estado de la mercadería al cargar, confirmar entrega, y dejar evidencia ante un reclamo o incidente.

### 29.2 — Qué requiere del lado del backend (pendiente, fuera del alcance de este documento mobile)

```sql
-- Tabla nueva en PostgreSQL
CREATE TABLE trip_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    trip_id UUID NULL REFERENCES trips(id),
    avl_user_id UUID NOT NULL REFERENCES avl_users(id),
    type VARCHAR(20) NOT NULL,          -- CARGO_START | CARGO_END | INCIDENT | OTHER
    notes TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    captured_at TIMESTAMPTZ NOT NULL,
    storage_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trip_attachments_vehicle ON trip_attachments(vehicle_id, captured_at);
```

- **Supabase Storage:** bucket `cargo-photos`, RLS por `tenant_id`, política de acceso solo lectura para el dashboard del tenant correspondiente.
- **Endpoint:** `POST /api/v1/trips/attachments` — recibe `multipart/form-data`, autentica con el mismo header `X-Hub-Api-Key`, sube la imagen a Storage, inserta la fila, y opcionalmente emite `attachment.new` por Socket.io para que el dashboard la muestre en vivo.
- **Autorización:** igual que telemetría — resuelve `avl_user_id` desde la API Key, y `vehicle_id` desde `plate` + `avl_user_id`.

### 29.3 — Decisiones de diseño mobile

- **Sin CameraX.** Se usa el contrato `ActivityResultContracts.TakePicture()` de Activity Result API, que delega en la app de cámara del sistema. Evita agregar una dependencia pesada para una funcionalidad simple (una foto por vez, sin preview custom).
- **Compresión antes de subir.** Las fotos de cámara moderna pesan 3-8 MB. Se comprimen a máximo 1280px de lado largo y calidad JPEG 72% antes de guardar — suficiente para validar visualmente el estado de la carga, liviano para subir con datos móviles.
- **Offline-first, igual que ubicaciones y eventos.** La foto se guarda comprimida en el almacenamiento privado de la app inmediatamente. Si hay red, se sube. Si no, queda en cola y un worker la reintenta.
- **Multipart, no JSON.** A diferencia de telemetría (JSON puro), el envío de imagen usa `multipart/form-data`. Por eso tiene su propio Retrofit service (`AttachmentApi`) y su propio worker (`AttachmentSyncWorker`) — no comparte el pipeline de `HubRawPayload`.

### Archivos
- `domain/model/AttachmentType.kt`
- `data/local/db/AttachmentEntity.kt`
- `data/local/db/AttachmentDao.kt`
- `data/remote/api/AttachmentApi.kt`
- `data/remote/sync/AttachmentSyncWorker.kt`
- `data/repository/AttachmentRepository.kt`
- `util/ImageCompressor.kt`
- `ui/attachments/AttachmentsViewModel.kt`
- `ui/attachments/AttachmentsScreen.kt`

### Archivo: `domain/model/AttachmentType.kt`
```kotlin
package com.rusertech.mobile.domain.model

enum class AttachmentType(val code: String, val displayName: String) {
    CARGO_START("CARGO_START", "Carga — Inicio"),
    CARGO_END("CARGO_END", "Carga — Entrega"),
    INCIDENT("INCIDENT", "Incidente"),
    OTHER("OTHER", "Otro")
}
```

### Archivo: `data/local/db/AttachmentEntity.kt`
```kotlin
package com.rusertech.mobile.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_attachments",
    indices = [Index(value = ["isUploaded", "timestamp"])]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localPath: String,       // ruta al JPEG comprimido en almacenamiento privado
    val type: String,            // AttachmentType.code
    val notes: String = "",
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isUploaded: Boolean = false,
    val remoteUrl: String? = null
)
```

### Archivo: `data/local/db/AttachmentDao.kt`
```kotlin
package com.rusertech.mobile.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AttachmentEntity): Long

    @Query("SELECT * FROM pending_attachments WHERE isUploaded = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnuploaded(limit: Int = 10): List<AttachmentEntity>

    @Query("UPDATE pending_attachments SET isUploaded = 1, remoteUrl = :url WHERE id = :id")
    suspend fun markUploaded(id: Long, url: String)

    @Query("SELECT * FROM pending_attachments ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<AttachmentEntity>>

    @Query("SELECT COUNT(*) FROM pending_attachments WHERE isUploaded = 0")
    fun getPendingCount(): Flow<Int>
}
```

> **`AppDatabase.kt` (Sección 9) ya incluye `AttachmentEntity` y `attachmentDao()`** — se actualizó ahí directamente para que el proyecto compile de punta a punta en una sola pasada, sin pasos de migración manual pendientes.

### Archivo: `util/ImageCompressor.kt`
```kotlin
package com.rusertech.mobile.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Comprime una foto tomada por la cámara a un JPEG liviano apto para subir
 * con datos móviles. 1280px de lado largo y calidad 72% son suficientes
 * para validar visualmente el estado de una carga sin generar archivos pesados.
 */
object ImageCompressor {
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 72

    fun compressToFile(context: Context, sourceUri: Uri, targetFile: File): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return false
            val original = BitmapFactory.decodeStream(input)
            input.close()

            val scale = MAX_DIMENSION.toFloat() / maxOf(original.width, original.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original, (original.width * scale).toInt(), (original.height * scale).toInt(), true
                )
            } else original

            FileOutputStream(targetFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (scaled != original) scaled.recycle()
            original.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }
}
```

### Archivo: `data/remote/api/AttachmentApi.kt`
```kotlin
package com.rusertech.mobile.data.remote.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import kotlinx.serialization.Serializable

@Serializable
data class AttachmentUploadResponse(val id: String, val url: String)

interface AttachmentApi {
    /**
     * Sube una foto de carga. multipart/form-data — no comparte el pipeline
     * de HubRawPayload porque transporta un binario, no JSON.
     * El backend resuelve vehicle_id desde plate + avlUserCode, igual que telemetría.
     */
    @Multipart
    @POST("api/v1/trips/attachments")
    suspend fun uploadAttachment(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Part("plate") plate: RequestBody,
        @Part("avlUserCode") avlUserCode: RequestBody,
        @Part("type") type: RequestBody,
        @Part("notes") notes: RequestBody,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<AttachmentUploadResponse>
}
```

### Archivo: `data/repository/AttachmentRepository.kt`
```kotlin
package com.rusertech.mobile.data.repository

import android.content.Context
import android.net.Uri
import com.rusertech.mobile.data.local.db.AttachmentDao
import com.rusertech.mobile.data.local.db.AttachmentEntity
import com.rusertech.mobile.data.remote.api.AttachmentApi
import com.rusertech.mobile.domain.model.AttachmentType
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.ImageCompressor
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AttachmentDao,
    private val api: AttachmentApi,
    private val networkUtil: NetworkUtil
) {
    private val storageDir: File
        get() = File(context.filesDir, "cargo_photos").apply { mkdirs() }

    /**
     * Comprime la foto tomada, la persiste localmente, e intenta subirla
     * de inmediato si hay red. Offline-first: la fila queda en Room
     * aunque falle la subida, y AttachmentSyncWorker reintenta después.
     */
    suspend fun saveAttachment(
        identity: UserIdentity,
        sourceUri: Uri,
        type: AttachmentType,
        notes: String,
        latitude: Double,
        longitude: Double
    ): Boolean {
        val targetFile = File(storageDir, "cargo_${System.currentTimeMillis()}.jpg")
        val compressed = ImageCompressor.compressToFile(context, sourceUri, targetFile)
        if (!compressed) return false

        val entity = AttachmentEntity(
            localPath = targetFile.absolutePath,
            type = type.code, notes = notes,
            latitude = latitude, longitude = longitude,
            timestamp = System.currentTimeMillis()
        )
        val id = dao.insert(entity)

        if (networkUtil.isOnline() && identity.apiKey.isNotBlank()) {
            tryUpload(identity, entity.copy(id = id))
        }
        return true
    }

    /** Llamado por AttachmentSyncWorker — sube de a una (multipart no soporta batch). */
    suspend fun syncPending(identity: UserIdentity): Result<Int> = runCatching {
        if (identity.apiKey.isBlank()) return@runCatching 0
        val pending = dao.getUnuploaded(10)
        var uploaded = 0
        for (attachment in pending) {
            if (tryUpload(identity, attachment)) uploaded++
        }
        uploaded
    }

    fun getRecent(): Flow<List<AttachmentEntity>> = dao.getRecent()
    fun getPendingCount(): Flow<Int> = dao.getPendingCount()

    private suspend fun tryUpload(identity: UserIdentity, entity: AttachmentEntity): Boolean {
        val file = File(entity.localPath)
        if (!file.exists()) return false
        return try {
            val imagePart = MultipartBody.Part.createFormData(
                "image", file.name, file.asRequestBody("image/jpeg".toMediaType())
            )
            val resp = api.uploadAttachment(
                apiKey = identity.apiKey,
                plate = identity.plate.toPlainRequestBody(),
                avlUserCode = identity.avlUserCode.toPlainRequestBody(),
                type = entity.type.toPlainRequestBody(),
                notes = entity.notes.toPlainRequestBody(),
                latitude = entity.latitude.toString().toPlainRequestBody(),
                longitude = entity.longitude.toString().toPlainRequestBody(),
                timestamp = entity.timestamp.toString().toPlainRequestBody(),
                image = imagePart
            )
            if (resp.isSuccessful) {
                dao.markUploaded(entity.id, resp.body()?.url ?: "")
                true
            } else false
        } catch (_: Exception) { false }
    }

    private fun String.toPlainRequestBody() = this.toRequestBody("text/plain".toMediaType())
}
```

### Archivo: `data/remote/sync/AttachmentSyncWorker.kt`
```kotlin
package com.rusertech.mobile.data.remote.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.repository.AttachmentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AttachmentSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val attachmentRepository: AttachmentRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val identity = userPreferences.snapshot() ?: return Result.failure()
        return try {
            val uploaded = attachmentRepository.syncPending(identity)
            Log.i("AttachmentSyncWorker", "Subidas ${uploaded.getOrDefault(0)} fotos")
            if (uploaded.isFailure) {
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            } else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
```

> **Registrar en `TrackingService.scheduleSyncWork()` (Sección 13):** agregar un segundo `PeriodicWorkRequestBuilder<AttachmentSyncWorker>(15, TimeUnit.MINUTES)` con las mismas `constraints`, encolado como `"rusertech_attachment_sync"`.

### Archivo: `ui/attachments/AttachmentsViewModel.kt`
```kotlin
package com.rusertech.mobile.ui.attachments

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.db.AttachmentEntity
import com.rusertech.mobile.data.repository.AttachmentRepository
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.domain.model.AttachmentType
import com.rusertech.mobile.service.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttachmentsViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    var selectedType by mutableStateOf(AttachmentType.CARGO_START); private set
    var notes by mutableStateOf(""); private set
    var lastSaveOk by mutableStateOf<Boolean?>(null); private set

    val recent: StateFlow<List<AttachmentEntity>> = attachmentRepository.getRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingCount: StateFlow<Int> = attachmentRepository.getPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onTypeSelected(type: AttachmentType) { selectedType = type }
    fun onNotesChange(value: String) { notes = value.take(200) }

    fun onPhotoCaptured(uri: Uri) {
        viewModelScope.launch {
            val identity = userRepository.snapshot() ?: return@launch
            val location = TrackingService.lastLocation.value
            val ok = attachmentRepository.saveAttachment(
                identity = identity, sourceUri = uri, type = selectedType, notes = notes,
                latitude = location?.latitude ?: 0.0, longitude = location?.longitude ?: 0.0
            )
            lastSaveOk = ok
            notes = ""
        }
    }
}
```

### Archivo: `ui/attachments/AttachmentsScreen.kt`
```kotlin
package com.rusertech.mobile.ui.attachments

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rusertech.mobile.data.local.db.AttachmentEntity
import com.rusertech.mobile.domain.model.AttachmentType
import com.rusertech.mobile.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttachmentsScreen(
    onBack: () -> Unit,
    viewModel: AttachmentsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingCaptureUri?.let { viewModel.onPhotoCaptured(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(deepSpaceGradient()).padding(20.dp).systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary)
            }
            Text("Fotos de carga", fontSize = 20.sp, fontWeight = FontWeight.W500, color = TextPrimary)
        }

        // Selector de tipo
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachmentType.entries.forEach { type ->
                FilterChip(
                    selected = viewModel.selectedType == type,
                    onClick = { viewModel.onTypeSelected(type) },
                    label = { Text(type.displayName, fontSize = 11.sp) }
                )
            }
        }

        OutlinedTextField(
            value = viewModel.notes, onValueChange = viewModel::onNotesChange,
            label = { Text("Notas (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        // Botón de cámara — gradiente Tech Glow
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(techGlowGradient(), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            TextButton(onClick = {
                val uri = createImageUri(context)
                pendingCaptureUri = uri
                cameraLauncher.launch(uri)
            }) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = DeepSpaceTop)
                Spacer(Modifier.width(8.dp))
                Text("Tomar foto", color = DeepSpaceTop, fontWeight = FontWeight.W500)
            }
        }

        if (pendingCount > 0) {
            Text("$pendingCount fotos pendientes de subir", fontSize = 12.sp, color = WarningAmber)
        }

        // Historial reciente
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(0.5.dp, SurfaceBorder)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Historial", fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                if (recent.isEmpty()) {
                    Text("Sin fotos registradas", fontSize = 14.sp, color = TextMuted,
                        modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    LazyColumn { items(recent, key = { it.id }) { AttachmentRow(it) } }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(attachment: AttachmentEntity) {
    val type = AttachmentType.entries.find { it.code == attachment.type }
    val timeStr = remember(attachment.timestamp) {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(attachment.timestamp))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(8.dp).background(
            if (attachment.isUploaded) SuccessGreen else WarningAmber, CircleShape
        ))
        Column(Modifier.weight(1f)) {
            Text(type?.displayName ?: attachment.type, fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextPrimary)
            Text(
                if (attachment.notes.isNotBlank()) "$timeStr · ${attachment.notes}" else timeStr,
                fontSize = 11.sp, color = TextMuted
            )
        }
        Text(
            if (attachment.isUploaded) "Subida" else "Pendiente",
            fontSize = 11.sp,
            color = if (attachment.isUploaded) SuccessGreen else WarningAmber
        )
    }
}

/** Crea un Uri temporal vía FileProvider para que la cámara del sistema escriba la foto. */
private fun createImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "cargo_photos").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
```

### 29.4 — Manifest y FileProvider

Ya integrado en el `AndroidManifest.xml` de la Sección 5: permiso `CAMERA`, feature `android.hardware.camera` (no requerida), y el `<provider>` de `FileProvider` apuntando a `app/src/main/res/xml/file_paths.xml`. No hace falta ningún paso adicional acá.

### 29.5 — Navegación

Ya integrado en el resto del documento (no requiere pasos adicionales):
- La ruta `"attachments"` está en `NavGraph.kt` (Sección 19)
- El parámetro `onNavigateToAttachments` y el botón "Fotos de carga" están en `TrackingScreen.kt` (Sección 17), junto al botón "Eventos y SOS"

### VERIFICAR
- [ ] `AppDatabase` incluye `AttachmentEntity` y `version = 2`
- [ ] La cámara del sistema abre al tocar "Tomar foto"
- [ ] La foto se comprime a <500 KB antes de guardarse
- [ ] Con el dispositivo en modo avión, la foto queda en "Pendiente" y sube sola al recuperar red
- [ ] El backend tiene la tabla `trip_attachments`, el bucket de Storage y el endpoint `/api/v1/trips/attachments` — **esto es trabajo del lado web, no de este documento**

---

## APÉNDICE A — Diagramas de Flujo

### A.1 — Flujo completo del usuario (de punta a punta)

```
┌─────────────────────────────────────────────────────────────────┐
│                    CONDUCTOR ABRE LA APP                         │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
              ┌───────────────┐
              │  SplashRoute  │
              │  Lee DataStore│
              └───────┬───────┘
                      │
            ┌─────────┴──────────┐
            │                    │
    ¿Tiene identidad?     No tiene identidad
            │                    │
            ▼                    ▼
    ┌───────────────┐   ┌────────────────────┐
    │ TrackingScreen │   │ RegistrationScreen │
    │ (volver)       │   │ 4 campos:          │
    └───────────────┘   │ · Documento         │
                        │ · Patente            │
                        │ · Código AVL         │
                        │ · API Key            │
                        └─────────┬────────────┘
                                  │ Guardar en DataStore
                                  ▼
                        ┌────────────────────┐
                        │   TrackingScreen    │
                        │  Botón: INICIAR     │
                        └─────────┬──────────┘
                                  │
                    ┌─────────────┴──────────────┐
                    │    FLUJO DE PERMISOS        │
                    │                             │
                    │  1. FINE_LOCATION            │
                    │  2. BACKGROUND_LOCATION      │
                    │     (API 29+, paso separado) │
                    │  3. POST_NOTIFICATIONS       │
                    │     (API 33+)                │
                    │  4. Battery Optimization     │
                    │  5. Guía OEM (si Xiaomi/     │
                    │     Samsung, una sola vez)   │
                    └─────────────┬──────────────┘
                                  │
                                  ▼
                        ┌────────────────────┐
                        │  TrackingService    │
                        │  startForeground()  │
                        │  START_STICKY       │
                        └─────────┬──────────┘
                                  │
                    ┌─────────────┴──────────────┐
                    │   LOOP DE TRACKING          │
                    │                             │
                    │  FusedLocationProvider      │
                    │  → LocationManager          │
                    │    → filtro accuracy <50m   │
                    │    → dedup estática         │
                    │    → intervalo adaptativo   │
                    │      (10s mov / 60s idle)   │
                    │                             │
                    │  Cada ubicación válida:     │
                    │  1. INSERT Room             │
                    │  2. ¿Hay red?               │
                    │     SÍ → POST ingest        │
                    │          → markSynced        │
                    │     NO → queda en cola       │
                    │  3. Check auto-eventos:     │
                    │     · batería ≤15%           │
                    │     · parada >5 min          │
                    └─────────────────────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
              ▼                   ▼                   ▼
    ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐
    │  WorkManager │   │  Eventos     │   │  BootReceiver    │
    │  cada 15 min │   │  manuales    │   │  tras reboot     │
    │  sync batch  │   │  SOS/CHKPT   │   │  auto-reanuda    │
    │  si hay red  │   │  /INCIDENT   │   │  si estaba activo│
    └──────────────┘   └──────────────┘   └──────────────────┘
```

### A.2 — Flujo de datos: App Mobile → Backend Web → Dashboard

```
┌──────────────────────────────────────────────────────────────────────┐
│                         APP MOBILE (Android)                         │
│                                                                      │
│  Room DB                     Retrofit                                │
│  ┌──────────────┐            ┌─────────────────────────────────┐     │
│  │ pending_     │  sync      │ POST /api/v1/telemetry/ingest   │     │
│  │ locations    │ ──────────>│ Header: X-Hub-Api-Key           │     │
│  │ tracking_    │            │ Body: HubRawPayload             │     │
│  │ events      │            │  · Asset = plate                 │     │
│  └──────────────┘            │  · User_avl = avlUserCode       │     │
│                              │  · Code = MOB_SOS | null        │     │
│                              │  · SourceTag = "mobile_app"     │     │
│                              └──────────────┬──────────────────┘     │
└─────────────────────────────────────────────┼────────────────────────┘
                                              │ HTTPS
                                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    BACKEND NESTJS (Rusertech Web)                     │
│                                                                      │
│  TelemetryIngestion Module                                           │
│  ┌────────────────────────────────────────────────────────────┐       │
│  │ 1. Validar X-Hub-Api-Key → resolver avl_user_id           │       │
│  │ 2. Asset (plate) + avl_user_id → resolver vehicle_id      │       │
│  │ 3. Deduplicar via Redis (vehicleId + timestamp)           │       │
│  │ 4. INSERT telemetry + INSERT outbox (1 transacción)       │       │
│  │ 5. UPDATE Redis: última posición del vehículo             │       │
│  └────────────────────────────────┬───────────────────────────┘       │
│                                   │ BullMQ: telemetry.raw            │
│  EventEngine Module               ▼                                  │
│  ┌────────────────────────────────────────────────────────────┐       │
│  │ · Lee diccionario del avl_user mobile                     │       │
│  │ · Code "MOB_SOS" → sos_alert (critical)                   │       │
│  │ · Code "MOB_CHKPT" → checkpoint_reached (info)            │       │
│  │ · Evalúa reglas del tenant (velocidad, geocercas, etc.)   │       │
│  │ · INSERT event_logs si corresponde                        │       │
│  │ · Dispara notificaciones (email/push/whatsapp/webhook)    │       │
│  └────────────────────────────────┬───────────────────────────┘       │
│                                   │ Socket.io                        │
│  PostgreSQL                       ▼                                  │
│  ┌────────────────┐    ┌────────────────────┐                        │
│  │ telemetry      │    │ Dashboard Web      │                        │
│  │ event_logs     │    │ MapLibre GL JS     │                        │
│  │ vehicles       │    │ Alertas en tiempo  │                        │
│  │ drivers        │    │ real               │                        │
│  │ trips          │    │ Historial de       │                        │
│  └────────────────┘    │ recorrido          │                        │
│                        └────────────────────┘                        │
└──────────────────────────────────────────────────────────────────────┘
```

### A.3 — Pseudocódigo: Ciclo de vida del TrackingService

```
CUANDO usuario toca "Iniciar seguimiento":
  1. Verificar permisos (FINE → BACKGROUND → NOTIFICATION)
  2. Solicitar exención de batería si no la tiene
  3. Mostrar guía OEM si es Xiaomi/Samsung (una vez)
  4. context.startForegroundService(ACTION_START)

CUANDO TrackingService recibe ACTION_START:
  1. SI serviceScope está cancelado → recrear (Fix #1)
  2. startForeground(notificación silenciosa IMPORTANCE_LOW)
  3. Leer identity de DataStore (documentId, plate, avlUserCode, apiKey)
  4. SI identity es null → stopSelf() y salir
  5. Guardar isTracking = true en DataStore
  6. locationManager.startUpdates()
  7. Programar WorkManager sync cada 15 min
  8. LOOP: por cada location del SharedFlow:
     a. Filtrar: accuracy > 50m → descartar
     b. Filtrar: distancia < 10m Y speed < 2 m/s → descartar (dedup)
     c. Construir LocationPoint
     d. INSERT en Room (pending_locations)
     e. SI hay red:
        · Construir HubRawPayload (Asset=plate, User_avl=code, Code=null)
        · POST /api/v1/telemetry/ingest con X-Hub-Api-Key
        · SI 2xx → markSynced en Room
        · SI falla → no hacer nada (WorkManager reintenta)
     f. Actualizar notificación (velocidad en km/h)
     g. Check auto-eventos:
        · SI batería ≤ 15% Y última alerta > 30 min → crear evento MOB_LOWBAT
        · SI velocidad < 2 m/s por > 5 min → crear evento MOB_STOP

CUANDO usuario toca "Detener seguimiento":
  1. locationManager.stopUpdates()
  2. isRunning = false
  3. Guardar isTracking = false en DataStore
  4. Cancelar collectJob y serviceScope
  5. stopForeground() + stopSelf()

CUANDO WorkManager dispara SyncWorker:
  1. Leer identity de DataStore
  2. SI identity es null → Result.failure()
  3. Leer eventos pendientes de Room (hasta 30)
  4. Convertir cada uno a HubRawPayload con Code = tipo del evento
  5. POST batch al endpoint de ingesta
  6. SI 2xx → markSynced + purgar datos viejos
  7. Leer ubicaciones pendientes de Room (hasta 50)
  8. Convertir cada una a HubRawPayload con Code = null
  9. POST batch
  10. SI falla Y intentos < 5 → Result.retry() (backoff exponencial)

CUANDO dispositivo se reinicia (BootReceiver):
  1. Leer isTracking de DataStore
  2. SI era true Y identity no es null:
     · context.startForegroundService(ACTION_START)
```

### A.4 — Pseudocódigo: Setup del operador en Rusertech Web

```
PASO 1 — Crear avl_user mobile:
  Dashboard → AVL Users → Nuevo
  · Nombre: "Flota Mobile [empresa]"
  · Tipo: mobile_app
  → El sistema genera API Key automáticamente
  → Anotar: avlUserCode y apiKey

PASO 2 — Cargar diccionario de códigos mobile:
  Dashboard → AVL Users → [mobile] → Diccionario → Agregar:
  ┌────────────────┬──────────────────────┬──────────┐
  │ Código (raw)   │ Evento estándar      │ Severity │
  ├────────────────┼──────────────────────┼──────────┤
  │ MOB_SOS        │ sos_alert            │ critical │
  │ MOB_COMM       │ communication_request│ info     │
  │ MOB_CHKPT      │ checkpoint_reached   │ info     │
  │ MOB_INCIDENT   │ incident_report      │ warning  │
  │ MOB_STOP       │ unauthorized_stop    │ warning  │
  │ MOB_LOWBAT     │ battery_low          │ warning  │
  └────────────────┴──────────────────────┴──────────┘

PASO 3 — Registrar vehículos:
  Dashboard → Flota → cada vehículo:
  · plate: la patente (tiene que coincidir con lo que el conductor ingresa en la app)
  · avl_user_id: el avl_user mobile creado en paso 1

PASO 4 — Configurar reglas de alerta:
  Dashboard → Alertas → Reglas:
  · sos_alert → acción: email + push + whatsapp (severidad: critical)
  · communication_request → acción: push (severidad: info)
  · Otras según necesidad del operador

PASO 5 — Entregar datos al conductor:
  El conductor necesita ingresar en la app:
  1. Su documento de identidad
  2. La patente del vehículo
  3. El código AVL (avlUserCode)
  4. La API Key
  → Estos datos se pueden distribuir en papel, QR, o pre-configurar en la app
```

---

## SECCIÓN 30 — Roadmap v1.2 (Fuera de Alcance de Esta Versión)

> Esta sección documenta decisiones evaluadas junto al operador que **no se implementan en v1.1** por relación costo/beneficio, pero que quedan definidas para no perder el criterio de diseño cuando se retomen.

### 30.1 — QR de configuración de flota (simplifica el registro a 2 campos)

**Problema que resuelve:** Hoy el conductor ingresa 4 campos en el registro (documento, patente, código AVL, API Key). Los dos últimos son datos técnicos que el conductor no debería necesitar tipear.

**Diseño propuesto:**
1. El operador genera un QR desde el dashboard web por cada `avl_user` de flota. El QR codifica un JSON: `{"avlUserCode": "...", "apiKey": "..."}`.
2. La app incorpora un botón "Escanear código de flota" en `RegistrationScreen`, que abre la cámara (misma técnica que Sección 29, sin dependencias nuevas de escaneo si se usa ML Kit Barcode Scanning — liviano, ~2 MB).
3. Al escanear, `avlUserCode` y `apiKey` se completan solos y esos campos pasan a `readOnly` u ocultos. El conductor solo ve Documento y Patente.

**Por qué no entró en v1.1:** Requiere que el dashboard web tenga la pantalla de generación de QR — trabajo del lado web que no estaba definido al momento de esta versión. La app está lista para consumirlo (la lógica de guardar `avlUserCode`/`apiKey` ya existe en `RegistrationViewModel`); falta la fuente del QR.

**Estimación:** ~4 horas del lado mobile (agregar ML Kit + pantalla de escaneo), más el trabajo de generación de QR del lado web.

### 30.2 — Desconexión remota instantánea vía Firebase Cloud Messaging

**Contexto:** La Sección 10.1 ya resuelve el caso base — el backend responde 403 y la app se detiene en el siguiente intento de sync (máximo unos segundos mientras trackea activamente, o hasta 15 min si el dispositivo está sin red y depende del WorkManager). Para la mayoría de los casos ("conductor terminó turno y no cerró la app") esto es más que suficiente.

**Cuándo hace falta más:** Si el operador necesita que el corte sea instantáneo — por ejemplo, revocar acceso en el mismo segundo por un incidente de seguridad — el mecanismo reactivo no alcanza porque depende de que la app intente un request.

**Diseño propuesto:**
1. Agregar Firebase Cloud Messaging al proyecto (`firebase-messaging` dependency + `google-services.json`).
2. Backend expone `POST /api/v1/mobile/disconnect` (nuevo endpoint) que recibe `avlUserCode` o `vehicleId`, resuelve el `fcmToken` guardado, y envía un push data-only (`{"action": "STOP_TRACKING"}`).
3. La app registra su `fcmToken` en el backend al iniciar sesión (nuevo campo `fcm_token` en la tabla de vehículos o en una tabla de sesiones mobile).
4. Un `FirebaseMessagingService` en la app recibe el push y ejecuta `TrackingService.ACTION_STOP` directamente, sin esperar al próximo ciclo de red.

**Por qué no entró en v1.1:** Es infraestructura nueva (FCM + endpoint + tabla de tokens) para un caso de uso que el mecanismo reactivo del 403 ya cubre razonablemente. Se prioriza no sobre-construir hasta confirmar que el caso instantáneo es realmente necesario en producción.

**Estimación:** ~1 día completo (mobile + backend).

### 30.3 — Odómetro acumulado por viaje

**Problema que resuelve:** Hoy cada punto de telemetría lleva lat/lng/velocidad, pero no hay un cálculo de distancia recorrida. El dashboard web tiene que inferirlo sumando distancias entre puntos consecutivos — impreciso si hay huecos de conectividad.

**Diseño propuesto:** `LocationManager` acumula la distancia (`Location.distanceTo()`) entre cada par de puntos válidos consecutivos dentro de una sesión de tracking, y ese acumulado se envía en el campo `Odometer` del `HubRawPayload` (que ya existe en el DTO — Sección 10 — pero hoy no se está poblando).

**Por qué no entró en v1.1:** Bajo esfuerzo, pero se decidió no mezclarlo con esta entrega para no introducir otra variable en el testing de campo inicial. Es el candidato más simple de implementar primero en v1.2.

**Estimación:** ~2 horas.

### 30.4 — Modo "parada autorizada"

**Problema que resuelve:** El evento automático `MOB_STOP` (Sección 13) se dispara cuando el vehículo está detenido más de 5 minutos, sin distinguir si es una parada normal (semáforo largo, carga, descanso) o algo que amerite atención. Esto puede generar ruido de falsas alertas en el dashboard.

**Diseño propuesto:** Agregar un botón en `EventsScreen` — "Marcar parada autorizada" — que el conductor toca antes de detenerse intencionalmente (ej: antes de bajar a cargar combustible). Mientras esté activo ese modo (con un timeout de, por ejemplo, 20 minutos), `checkAutoEvents()` no dispara `MOB_STOP`.

**Por qué no entró en v1.1:** Es una mejora de calidad de las alertas, no un bloqueante funcional. Vale la pena verla en producción real un tiempo para calibrar si el umbral de 5 minutos genera ruido antes de decidir la UX exacta de este modo.

**Estimación:** ~3 horas.

### 30.5 — Tabla resumen del roadmap

| # | Mejora | Esfuerzo | Depende de |
|---|--------|----------|-----------|
| 30.1 | QR de configuración de flota | ~4h mobile + trabajo web | Pantalla de generación de QR en dashboard |
| 30.2 | Desconexión instantánea vía FCM | ~1 día | Nada — se puede iniciar cuando se priorice |
| 30.3 | Odómetro acumulado | ~2h | Nada — más simple de implementar primero |
| 30.4 | Modo parada autorizada | ~3h | Datos de producción para calibrar el timeout |

---

## SECCIÓN 31 — Checklist de Publicación en Play Store

> Esta sección no es sobre el código de la app — es sobre lo que hay que tener resuelto en **Play Console** para que la publicación no quede trabada en revisión. Está ordenada por cuándo hay que resolver cada cosa, no por importancia.

### 31.1 — Cuenta de desarrollador Personal: cómo cumplir el testing cerrado sin perder tiempo

Rusertech va a registrar la cuenta de Play Console como **Personal**, no Organización — así que el gate de testing cerrado aplica sí o sí. Esto es lo que exige en concreto y cómo resolverlo sin que se transforme en una demora innecesaria.

**❌ Requisito obligatorio:** cuentas personales creadas después del 13 de noviembre de 2023 deben correr un testing cerrado con **mínimo 12 testers opted-in de forma continua durante 14 días** antes de que Play Console habilite el pedido de acceso a Producción. Esto no cambió en 2026 — bajó de 20 a 12 testers en diciembre de 2024, pero los 14 días consecutivos siguen igual.

**Lo que SÍ cuenta como testing válido:**
- Testers reales, con cuenta de Google propia, que entran al programa mediante el **link de opt-in oficial de Play Console** (no sideload del APK por WhatsApp/Drive)
- Instalación desde la Play Store (track de "Pruebas cerradas"), no un APK suelto
- Uso real y periódico durante los 14 días — Google mide engagement, no solo instalación. Un tester que instala una vez y no vuelve a abrir la app cuenta como inactivo y puede hacer fallar el pedido de acceso a producción por "insufficient testing engagement"
- Si un tester se da de baja o queda inactivo y el conteo cae debajo de 12 en cualquier momento, el conteo de días se ve afectado — conviene arrancar con margen

**La recomendación concreta para Rusertech — no compres testers:**
Existen servicios pagos que venden "12 testers garantizados" por unos pocos dólares. No los recomiendo: además de la zona gris de cumplir la letra de la política sin cumplir su espíritu (verificar que el testing sea genuino), Rusertech tiene un candidato mucho mejor a mano — **usar los conductores/operadores reales del primer piloto de flota como testers.** Esto resuelve dos cosas a la vez: cumple el requisito de Google con testers 100% legítimos, y da feedback de campo real antes del lanzamiento público — que es más valioso que pasar el gate por sí solo. Si el piloto todavía no tiene 12 personas, sumar al equipo interno de Rusertech (administración, ventas, cualquiera con un Android a mano) para completar el número.

**Timeline para presupuestar (no es instantáneo):**
```
Día 0:    Build firmado (.aab, Sección 24) subido al track de Pruebas cerradas
          Google revisa ese primer build antes de habilitar el link de opt-in
          (puede tardar unos días en cuentas nuevas)
Día X:    Los 12+ testers reciben el link de opt-in, se suman e instalan
          desde la Play Store. El reloj de 14 días arranca recién cuando
          los 12 están opted-in — no desde que se crea el track.
Día X+14: Si se mantuvieron 12+ testers activos todo el período,
          se habilita el botón "Solicitar acceso a producción" en el Dashboard
Día X+14 a X+21: Google revisa la solicitud de acceso a producción
          (Google indica que normalmente tarda 7 días o menos)
```

En total, contar **entre 3 y 4 semanas** desde que hay un build listo hasta que Play Console habilita la publicación pública — no es algo para resolver la semana del lanzamiento. Conviene arrancar el track de pruebas cerradas apenas exista un `.aab` funcional, en paralelo con el resto del checklist de esta sección, no al final.

**Durante la ventana de 14 días:** evitar subir un nuevo `.aab` al track de pruebas — actualizar el build en medio del período de testing es una práctica de riesgo para la continuidad del conteo. Si hay que corregir algo, mejor terminar la ventana de 14 días con el build actual y iterar después.

Costo: USD 25, pago único, por cuenta.

### 31.2 — targetSdk 36 (Android 16)

Ya resuelto en el código — Secciones 2 y 4 fijan `compileSdk = 36` / `targetSdk = 36`, con AGP 8.9.1 y Gradle Wrapper 8.11.1, que son los mínimos requeridos para compilar contra API 36.

**❌ Obligatorio:** Play Console rechaza (no solo advierte) la publicación de apps nuevas con `targetSdk` menor a 36 a partir del 31 de agosto de 2026. Si por algún motivo alguien clona este documento y baja el `targetSdk` "para simplificar", la publicación va a fallar en el paso de revisión automática, no en el humano — se entera recién al intentar subir el `.aab`.

### 31.3 — Declaración de Ubicación en Segundo Plano

Esto **no es un checkbox** — es un formulario dedicado en Play Console (`Política de la app → Permisos sensibles → Ubicación`) que pide:

1. **Justificación escrita** de por qué el tracking continuo en background es funcionalidad núcleo del producto, no un "nice to have". Para Rusertech esto es directo: es tracking de seguridad y logística, la app literalmente no cumple su propósito sin ubicación en background — pero hay que redactarlo explícitamente, no alcanza con que sea "obvio".
2. **Video de máximo 30 segundos** mostrando el flujo real: abrir la app, iniciar sesión/registro, y el momento exacto donde se concede el permiso "Permitir todo el tiempo". Grabarlo directo desde un dispositivo real corriendo el APK de Sección 24 — no vale un mockup ni el simulador de Gemini, tiene que ser la app nativa.
3. **Ficha de la tienda actualizada** — la descripción pública y las capturas de pantalla deben dejar en claro que la app usa ubicación en background. No puede quedar como sorpresa para quien la instale.

Desde la actualización de política de abril 2026, Google endureció la revisión de este punto específicamente: casos que antes pasaban con una justificación genérica ahora requieren el video y la declaración formal completa. Presupuestar tiempo de revisión — no es instantáneo, Google puede tardar días en aprobar este formulario específico, independiente del resto de la app.

### 31.4 — Política de Privacidad

**❌ Obligatorio, bloqueante desde el primer envío a revisión:**
- URL activa y pública (no un PDF, no un Google Doc con permisos raros)
- No editable por terceros
- Debe describir específicamente: qué datos de ubicación se recolectan, con qué frecuencia, quién los ve (el operador de la flota vía dashboard), cuánto se retienen, y que las fotos de carga (Sección 29) también se suben con georreferencia
- Enlazada tanto en la ficha de Play Store como dentro de la app (agregar el link en `RegistrationScreen`, cerca del disclaimer de "tus datos se guardan localmente")

Si Rusertech Web ya tiene una política de privacidad para el dashboard, extenderla para cubrir la app mobile es más rápido que redactar una nueva desde cero — pero tiene que mencionar explícitamente la recolección de ubicación en background y fotos, que son cosas que el dashboard web por sí solo no hace.

### 31.5 — Formulario de Data Safety

Sección obligatoria en Play Console (`Política de la app → Seguridad de los datos`) donde se declara, tipo por tipo, qué datos recolecta la app:

| Tipo de dato | ¿Se recolecta? | ¿Se comparte? | Propósito declarado |
|---|---|---|---|
| Ubicación precisa | Sí | Sí (con el operador de la flota) | Funcionalidad de la app (tracking) |
| Fotos | Sí | Sí (con el operador) | Funcionalidad de la app (validación de carga) |
| Identificadores de dispositivo | Indirecto (vía DataStore local) | No | — |
| Datos personales (documento) | Sí | Sí (con el operador) | Identificación del conductor |

Este formulario se audita contra el comportamiento real de la app durante la revisión — si declarás algo que el código no hace, o si el código hace algo no declarado, es motivo de rechazo. Como referencia rápida: mirar Sección 10 (`HubRawPayload`) y Sección 29 (`AttachmentApi`) para la lista exacta de qué sale del dispositivo.

### 31.6 — Firma y Release

Ya cubierto en Sección 24 (`keytool`, `signingConfigs`, `bundleRelease`). Un detalle adicional para Play Console específicamente:

- Habilitar **Play App Signing** (Google gestiona la clave de firma final; vos mantenés la de subida/upload key). Es el estándar recomendado por Google desde hace años — permite recuperar acceso si se pierde la keystore original, cosa que con firma 100% manual no tiene solución.
- Subir el `.aab` de `bundleRelease`, no el `.apk` — Play Store exige Android App Bundle para apps nuevas.

### 31.7 — Cuestionario de Clasificación de Contenido

Formulario estándar de Play Console sobre violencia, contenido para adultos, etc. Para Rusertech es directo — sin contenido sensible, clasificación esperada: apto para todo público o similar. El único punto no trivial: declarar que la app **no está dirigida a niños** (`Target Audience`), dado que es una herramienta B2B para conductores adultos.

### 31.8 — Checklist consolidado, en orden de ejecución

```
ANTES de crear la cuenta de Play Console:
  [ ] Tener la política de privacidad redactada y publicada en una URL propia
  [ ] Identificar de antemano quiénes van a ser los 12+ testers del piloto
      (conductores/operadores del piloto de flota + equipo interno de respaldo)

AL crear la cuenta:
  [ ] Registrar como cuenta Personal
  [ ] Pagar el fee de USD 25
  [ ] Completar verificación de identidad (puede tardar hasta 48hs)

ANTES de subir el primer build:
  [ ] Confirmar compileSdk=36 / targetSdk=36 en el .aab generado (Secciones 2 y 4)
  [ ] Habilitar Play App Signing
  [ ] Grabar el video de 30s del flujo de permiso de ubicación en background,
      en un dispositivo real con el APK compilado (no el simulador)

Arrancar el gate de testing cerrado LO ANTES POSIBLE (no al final):
  [ ] Subir el .aab al track de "Pruebas cerradas" en Play Console
  [ ] Esperar la revisión inicial de Google sobre ese primer build
  [ ] Enviar el link de opt-in oficial a los 12+ testers reales
  [ ] Confirmar que los 12 quedaron opted-in (acá arranca el reloj de 14 días)
  [ ] Monitorear engagement durante la ventana — no subir un .aab nuevo mientras corre
  [ ] Día 14: solicitar acceso a Producción desde el Dashboard

Al completar la ficha de Play Console (en paralelo a los 14 días de testing):
  [ ] Formulario de declaración de Ubicación en Segundo Plano + video + justificación escrita
  [ ] Formulario de Data Safety (tabla de 31.5)
  [ ] Cuestionario de Clasificación de Contenido
  [ ] Link a política de privacidad en la ficha de la tienda
  [ ] Descripción y capturas que mencionen explícitamente el uso de ubicación en background

Antes de enviar a revisión de producción:
  [ ] Testeo en dispositivos reales completo (Sección 25: Samsung, Xiaomi, Huawei)
  [ ] Link de política de privacidad también agregado dentro de la app (RegistrationScreen)

Después de enviar:
  [ ] Presupuestar ~7 días adicionales para la revisión de acceso a producción
  [ ] Presupuestar días adicionales de revisión específicamente por el permiso de
      background location — no asumir que el resto de la app se aprueba al mismo tiempo
```

**Total estimado de este checklist, de punta a punta:** entre 3 y 4 semanas desde que hay un `.aab` funcional, contando la ventana de 14 días de testing cerrado más las revisiones de Google. Vale la pena arrancar el track de pruebas cerradas apenas el build compile, en paralelo con el resto de los formularios — no es un paso que se pueda comprimir apurando testers de último momento.

### VERIFICAR
- [ ] 12+ testers reales identificados de antemano (idealmente conductores/operadores del piloto de flota)
- [ ] Track de "Pruebas cerradas" activo en Play Console con los 12+ testers opted-in de forma continua
- [ ] Los 14 días consecutivos se cumplieron sin que el conteo cayera debajo de 12
- [ ] `compileSdk`/`targetSdk` en 36 confirmado en el `.aab` subido
- [ ] Video de 30s grabado desde dispositivo real, no desde el simulador
- [ ] Política de privacidad pública, enlazada en la ficha y dentro de la app
- [ ] Formulario de Data Safety completado y coincide con lo que el código realmente envía (Secciones 10 y 29)

---

## FIN DE LA ESPECIFICACIÓN v1.1 COMPLETO

Este documento es **autocontenido**. Contiene todo el código necesario para construir el proyecto desde cero. No requiere ningún documento adicional.

### Resumen de archivos

| Categoría | Cantidad |
|-----------|----------|
| Kotlin fuente | ~39 |
| XML recursos | ~7 |
| Configuración (Gradle, ProGuard, Gradle Wrapper) | ~5 |
| Tests | ~4 |

### Resumen de secciones

| Rango | Contenido |
|-------|-----------|
| 1-6 | Setup del proyecto, Gradle (AGP 8.9.1 / SDK 36), Manifest, recursos, logo |
| 7 | Design system (colores compartidos con Rusertech Web) |
| 8-12 | Dominio, Room, API (formato HUB), manejo diferenciado de 401/403 (10.1), repositorios, utilidades LATAM |
| 13-15 | Foreground Service (reacción distinta a 401 vs 403), WorkManager, BootReceiver, Hilt DI |
| 16-22 | UI Compose (registro, tracking, eventos), navegación, permisos, tests |
| 23-25 | Contrato backend (HUB ingest), build/release, checklist producción |
| 26 | Compatibilidad OEM (Xiaomi, Samsung) y batería |
| 27 | iOS evaluado como fase futura |
| 28 | Integración con Rusertech Web (Opción A: mobile como HUB) |
| 29 | Captura de fotos de carga (requiere backend nuevo — ver advertencia en la sección) |
| 30 | Roadmap v1.2: QR de configuración, desconexión FCM, odómetro, parada autorizada |
| 31 | Checklist de publicación en Play Store: cuenta, targetSdk 36, declaración de ubicación, Data Safety |
| Apéndice A | Diagramas de flujo y pseudocódigo |

### Fixes de auditoría aplicados
- ❌ Fix #1: serviceScope se recrea tras stop/restart
- ❌ Fix #5: stopTracking() usa stopService()
- ❌ Fix #6: HttpLoggingInterceptor solo DEBUG, solo HEADERS
- ⚠️ Fix #8: Deduplicación de posición estática
- ⚠️ Fix #9: Filtro de accuracy > 50m

### Novedades de esta entrega
- **Revocación de acceso diferenciada (Sección 10.1):** `AuthInterceptor` ahora distingue 401 de 403 — `403` (acceso revocado deliberadamente) detiene el tracking y bloquea el botón de inicio; `401` (API Key mal formada) **no detiene nada**, solo muestra un banner ámbar mientras la app sigue guardando todo localmente. Antes ambos códigos se trataban igual; esta versión separa el caso "error de tipeo" del caso "el operador decidió cortar el acceso".
- **`targetSdk`/`compileSdk` actualizado a 36 (Secciones 2 y 4):** Play Console exige Android 16 (API 36) o superior para apps nuevas desde el 31/08/2026. Se actualizó AGP a 8.9.1 y se agregó `gradle-wrapper.properties` con Gradle 8.11.1, que son los mínimos requeridos para compilar contra esa API.
- **Fotos de carga (Sección 29):** captura offline-first vía cámara del sistema, compresión local, subida multipart con reintento por WorkManager. Requiere trabajo nuevo del lado del backend web (tabla, bucket, endpoint) — documentado explícitamente para no asumir que ya existe.
- **Roadmap explícito (Sección 30):** QR de configuración de flota, desconexión instantánea vía FCM, odómetro acumulado y modo de parada autorizada quedan definidos en diseño pero fuera del alcance de esta versión, con esfuerzo estimado y dependencias claras para cuando se prioricen.
- **Checklist de Play Store (Sección 31, nueva):** cubre lo que no es código pero bloquea la publicación si no está resuelto — cómo cumplir el gate de 12 testers/14 días con cuenta Personal (usando testers reales del piloto de flota, no servicios pagos), declaración formal de ubicación en background con video de 30s, política de privacidad, formulario de Data Safety, y un checklist consolidado en orden de ejecución con timeline realista (3-4 semanas de punta a punta).

