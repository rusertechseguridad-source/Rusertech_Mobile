package com.rusertech.mobile.util

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationUtil {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Abre el LISTADO de optimización de batería para que el conductor exima
     * la app a mano (I7).
     *
     * Deliberadamente NO se usa ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     * (el diálogo directo): requiere declarar el permiso
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, que Play audita con lupa y puede
     * costar el rechazo de la app. Esta variante no requiere ningún permiso.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
