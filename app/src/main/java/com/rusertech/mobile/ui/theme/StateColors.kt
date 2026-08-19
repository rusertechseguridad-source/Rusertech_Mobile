package com.rusertech.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import com.rusertech.mobile.domain.model.DriverState

/**
 * B1 (tanda 6) — semántica de color de estados y eventos, en UN solo lugar.
 *
 *  - EN_ROUTE           → verde Tech Glow (circulando, todo normal)
 *  - stopped_* (declaradas) → azul Tech Glow #2AB3FF: detenido pero
 *    declarado, legítimo — la otra punta del gradiente
 *  - MOB_STOP (NO declarada) → ámbar: es LA señal de seguridad, salta a la vista
 */
fun driverStateColor(state: DriverState): Color =
    if (state.isDeclaredStop) TechGlowBlue else TechGlowCyan

fun eventColor(code: String): Color = when (code) {
    "MOB_SOS" -> SOSRed
    "MOB_STOP" -> WarningAmber                                   // parada NO declarada
    "MOB_WAYPOINT", "MOB_STOP_AUTH", "MOB_STOP_SANIT" -> TechGlowBlue  // declaradas
    "MOB_RESUME" -> TechGlowCyan
    "MOB_CHKPT" -> SuccessGreen
    "MOB_COMM" -> InfoBlue
    "MOB_INCIDENT", "MOB_LOWBAT" -> WarningAmber
    else -> TextSecondary
}
