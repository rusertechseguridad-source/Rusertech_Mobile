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
            // Fix #6: solo headers en debug, sin PII en body.
            // FIX-8: la API Key se REDACTA — debug apunta a producción, y sin
            // esto la credencial real del tenant queda en logcat de cualquier
            // teléfono con una build debug.
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                    redactHeader("X-Hub-Api-Key")
                })
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

    @Provides @Singleton
    fun provideAuthApi(retrofit: Retrofit): com.rusertech.mobile.data.remote.api.AuthApi = retrofit.create(com.rusertech.mobile.data.remote.api.AuthApi::class.java)

    // FIX-2: TripApi sale del MISMO Retrofit autenticado que TrackingApi.
    @Provides @Singleton
    fun provideTripApi(retrofit: Retrofit): com.rusertech.mobile.data.remote.api.TripApi = retrofit.create(com.rusertech.mobile.data.remote.api.TripApi::class.java)

    @Provides @Singleton
    fun provideMapApi(json: Json): com.rusertech.mobile.data.remote.api.MapApi {
        val publicOkHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            // B2 (tanda 6) / M6: Nominatim EXIGE un User-Agent identificable —
            // sin él, OSM devuelve 403 esporádico y la búsqueda "no funciona".
            // Aplica también a OSRM. Es la causa raíz de la búsqueda muerta.
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Rusertech-Mobile/1.0 (operaciones@rusertech.com)")
                        .build()
                )
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }.build()

        return Retrofit.Builder()
            .baseUrl("https://dummy.com/") // Overridden by @Url
            .client(publicOkHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(com.rusertech.mobile.data.remote.api.MapApi::class.java)
    }
}
