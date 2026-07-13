package com.rusertech.mobile.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rusertech.mobile.BuildConfig
import com.rusertech.mobile.data.remote.api.AttachmentApi
import com.rusertech.mobile.data.remote.api.TrackingApi
import com.rusertech.mobile.data.remote.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Provides @Singleton
    fun provideOkHttp(authInterceptor: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(true)
        .addInterceptor(authInterceptor)  // Detecta 401/403 → AuthEventBus (Sección 10.1)
        .apply {
            // Fix #6: solo headers en debug, sin PII en body
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS })
            }
        }.build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_BASE_URL).client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()

    @Provides @Singleton
    fun provideTrackingApi(retrofit: Retrofit): TrackingApi = retrofit.create(TrackingApi::class.java)

    @Provides @Singleton
    fun provideAttachmentApi(retrofit: Retrofit): AttachmentApi = retrofit.create(AttachmentApi::class.java)
}
