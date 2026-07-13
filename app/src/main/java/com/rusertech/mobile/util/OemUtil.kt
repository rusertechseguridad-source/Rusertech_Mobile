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
