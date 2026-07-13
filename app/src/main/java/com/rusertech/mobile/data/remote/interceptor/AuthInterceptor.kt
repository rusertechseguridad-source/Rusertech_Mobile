package com.rusertech.mobile.data.remote.interceptor

import com.rusertech.mobile.data.remote.api.AuthEventBus
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepta cada respuesta HTTP y distingue el motivo del rechazo:
 * - 403 → el avl_user fue desactivado o la API Key fue revocada a propósito.
 * - 401 → la API Key está mal formada (típicamente un typo de carga).
 * Cada código dispara un evento distinto en AuthEventBus — ver Sección 10.1.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authEventBus: AuthEventBus
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        when (response.code) {
            403 -> authEventBus.notifyAccessRevoked()
            401 -> authEventBus.notifyCredentialWarning()
        }
        return response
    }
}
