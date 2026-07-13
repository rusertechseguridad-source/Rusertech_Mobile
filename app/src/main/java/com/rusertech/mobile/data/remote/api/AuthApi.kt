package com.rusertech.mobile.data.remote.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class LoginRequest(
    val documentId: String,
    val plate: String
)

@Serializable
data class LoginResponse(
    val avlUserCode: String,
    val apiKey: String
)

interface AuthApi {
    @POST("api/v1/mobile/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}
