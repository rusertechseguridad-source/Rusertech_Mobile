package com.rusertech.mobile.domain.model

/**
 * Parámetros operativos del tracking, configurables por tenant desde el
 * backend (llegan en la respuesta de login) con default local para cada uno.
 *
 * Regla de tolerancia: si el backend no envía la configuración —o la envía
 * parcial—, la app opera con estos defaults sin ningún error. La ausencia de
 * configuración remota es un estado normal, no una falla.
 *
 * El contrato de intercambio está documentado en CONTRATO_CONFIG_OPERATIVA.md.
 */
data class OperationalConfig(
    /** Intervalo del heartbeat de presencia con tracking activo. */
    val heartbeatIntervalMinutes: Int = 5,
    /** Minutos detenido sin declarar antes de emitir MOB_STOP. */
    val stopThresholdMinutes: Int = 5,
    /** Intervalo de muestreo GPS en movimiento. */
    val intervalMovingSeconds: Int = 5,
    /** Intervalo de muestreo GPS detenido. */
    val intervalIdleSeconds: Int = 30,
    /** Desplazamiento mínimo para persistir un punto. */
    val minDisplacementMeters: Float = 10f,
    /** Precisión máxima aceptada de un fix; peores se descartan. */
    val maxAccuracyMeters: Float = 50f,
    /** Minutos de movimiento continuo para el MOB_RESUME automático. */
    val autoResumeMinutes: Int = 3,
) {
    val heartbeatIntervalMs: Long get() = heartbeatIntervalMinutes * 60_000L
    val stopThresholdMs: Long get() = stopThresholdMinutes * 60_000L
    val intervalMovingMs: Long get() = intervalMovingSeconds * 1_000L
    val intervalIdleMs: Long get() = intervalIdleSeconds * 1_000L
    val autoResumeMs: Long get() = autoResumeMinutes * 60_000L
}
