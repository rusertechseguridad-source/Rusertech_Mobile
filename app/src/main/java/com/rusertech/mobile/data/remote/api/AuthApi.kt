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

@Serializable
data class LoginResponse(
    val avlUserCode: String,
    val apiKey: String
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
