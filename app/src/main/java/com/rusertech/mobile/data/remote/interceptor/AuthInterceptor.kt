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
 *
 * FIX-3: el endpoint de login queda FUERA de este tratamiento. Ahí un 401/403
 * habla del código de activación que el conductor acaba de tipear, no de la
 * credencial de tracking — que en ese momento ni siquiera existe. Sin esta
 * exclusión, tipear mal el código de activación dispararía la notificación
 * "Revisá tus credenciales" del tracking, o peor, un 403 del login detendría
 * un tracking que nunca arrancó.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authEventBus: AuthEventBus
) : Interceptor {

    private companion object {
        const val LOGIN_PATH = "/mobile/login"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        val path = chain.request().url.encodedPath
        if (!path.contains(LOGIN_PATH)) {
            when (response.code) {
                403 -> authEventBus.notifyAccessRevoked()
                401 -> authEventBus.notifyCredentialWarning()
            }
        }
        return response
    }
}
