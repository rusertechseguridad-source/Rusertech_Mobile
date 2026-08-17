package com.rusertech.mobile.util

/**
 * Instrucciones de configuración por fabricante para que el Foreground
 * Service sobreviva a los battery killers de los OEM.
 *
 * Auditoría white-label: el nombre de la app entra por parámetro — este
 * archivo no conoce ninguna marca. El llamador pasa
 * `context.getString(R.string.app_name)`.
 */
object OemUtil {
    fun getManufacturer(): String = android.os.Build.MANUFACTURER.lowercase()

    fun needsSpecialSetup(): Boolean =
        getManufacturer() in listOf("xiaomi", "samsung", "huawei", "oppo", "vivo", "realme", "oneplus")

    fun getSetupInstructions(appName: String): String? = when (getManufacturer()) {
        "xiaomi" -> """
            Para que el seguimiento funcione correctamente en tu Xiaomi:
            1. Configuración → Apps → $appName → Ahorro de batería → Sin restricciones
            2. En recientes, mantené presionada la app y tocá el candado
            3. Configuración → Apps → Permisos → Autostart → activá $appName
        """.trimIndent()
        "samsung" -> """
            Para que el seguimiento funcione correctamente en tu Samsung:
            1. Configuración → Cuidado del dispositivo → Batería → Límites de uso en segundo plano
            2. Asegurate de que $appName NO esté en "Apps en suspensión"
            3. Agregá $appName a "Apps que nunca se suspenden"
        """.trimIndent()
        "oppo", "realme" -> """
            Para que el seguimiento funcione correctamente:
            1. Configuración → Batería → Optimizar uso de batería
            2. Buscá $appName y seleccioná "No optimizar"
        """.trimIndent()
        else -> null
    }
}
