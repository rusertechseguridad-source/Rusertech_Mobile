package com.rusertech.mobile.util

import android.content.Context
import android.os.BatteryManager

object BatteryUtil {
    fun getLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(-1, 100)
    }
}
