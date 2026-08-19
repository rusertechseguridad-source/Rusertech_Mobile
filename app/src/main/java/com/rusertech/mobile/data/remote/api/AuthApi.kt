package com.rusertech.mobile.data.remote.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class LoginRequest(
    val documentId: String,
    val plate: String,
    val activationCode: String
)

/**
 * Configuración operativa por tenant, opcional en la respuesta de login.
 * Todos los campos son opcionales: cualquier ausencia (campo, objeto entero,
 * backend sin la feature) se resuelve con los defaults locales de
 * OperationalConfig. Contrato completo: CONTRATO_CONFIG_OPERATIVA.md.
 */
@Serializable
data class OperationalConfigDto(
    val heartbeatIntervalMinutes: Int? = null,
    val stopThresholdMinutes: Int? = null,
    val intervalMovingSeconds: Int? = null,
    val intervalIdleSeconds: Int? = null,
    val minDisplacementMeters: Float? = null,
    val maxAccuracyMeters: Float? = null,
    val autoResumeMinutes: Int? = null
)

@Serializable
data class LoginResponse(
    val avlUserCode: String,
    val apiKey: String,
    // Opcional: el backend actual no lo envía y la app funciona igual.
    val config: OperationalConfigDto? = null
)

interface AuthApi {
    /**
     * Único endpoint sin X-Hub-Api-Key.
     *
     * Devuelve `Response<LoginResponse>` y NO el body pelado: el repositorio
     * necesita leer el código HTTP para distinguir 401 / 403 / 404 / 429 / 5xx,
     * y con el body pelado Retrofit tiraría HttpException en todos los casos,
     * borrando esa distinción.
     */
    @POST("api/v1/mobile/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
