# Rusertech Mobile

**Rusertech Mobile** es una aplicación de tracking GPS de baja fricción diseñada para trabajadores de seguridad y logística en Latinoamérica.

## Características Principales

*   **Identificación Simple:** Registro con solo el Documento de Identidad y la Patente del Vehículo (sin correos electrónicos ni contraseñas).
*   **Tracking en Segundo Plano:** Monitoreo continuo de ubicación utilizando Foreground Services.
*   **Gestión de Eventos:** Generación de eventos (SOS, Checkpoints, Solicitudes de Contacto, Incidentes) enviada en tiempo real.
*   **Captura de Fotos (Prueba de Carga):** Posibilidad de capturar y subir fotos de comprobantes de carga/incidentes asociadas con los datos georreferenciados.
*   **Offline-First:** Persistencia robusta utilizando Room. Todos los eventos generados offline son sincronizados cuando la conectividad se restablece mediante WorkManager.
*   **Mobile como HUB:** Integración nativa con la plataforma Rusertech Web compartiendo el pipeline de telemetría y eventos.

## Tecnologías Utilizadas

*   **Lenguaje:** Kotlin
*   **UI:** Jetpack Compose (Material 3)
*   **Arquitectura:** MVVM, Clean Architecture, Offline-first
*   **Base de datos Local:** Room
*   **Inyección de Dependencias:** Hilt
*   **Gestión de Red:** Retrofit2 + OkHttp + Kotlinx Serialization
*   **Sincronización:** WorkManager
*   **Ubicación:** FusedLocationProvider (Google Play Services)

## Requisitos de Entorno
- Android Studio Ladybug (2024.2+)
- JDK 17
- SDK Platform 36 (Android 16 Baklava)

## Referencia de Configuración y Documentación
Para información detallada del diseño técnico, revisión de checklist de producción y requerimientos de publicación en Google Play Console, referirse al documento: `RUSERTECH_MOBILE_IMPLEMENTATION_v1.1_COMPLETO.md`.
