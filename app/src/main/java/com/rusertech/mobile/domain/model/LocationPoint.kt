package com.rusertech.mobile.domain.model

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,         // m/s
    val heading: Float,       // grados 0-360
    val altitude: Double,
    val battery: Int,         // 0..100
    val timestamp: Long,      // milisegundos epoch
    val tripId: String? = null // null si es Seguimiento Libre
) {
    fun speedKmh(): Float = speed * 3.6f
}
