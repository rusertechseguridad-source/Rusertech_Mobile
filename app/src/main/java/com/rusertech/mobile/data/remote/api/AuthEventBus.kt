package com.rusertech.mobile.data.remote.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canal de eventos de autenticación entre la capa de red y el resto de la app.
 * AuthInterceptor emite acá según el código HTTP recibido:
 * - accessRevoked (403): TrackingService detiene el servicio.
 * - credentialWarning (401): TrackingService NO detiene nada, solo avisa a la UI.
 */
@Singleton
class AuthEventBus @Inject constructor() {
    private val _accessRevoked = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val accessRevoked: SharedFlow<Unit> = _accessRevoked.asSharedFlow()

    private val _credentialWarning = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val credentialWarning: SharedFlow<Unit> = _credentialWarning.asSharedFlow()

    /** 403 — acceso revocado deliberadamente por el operador. */
    fun notifyAccessRevoked() {
        _accessRevoked.tryEmit(Unit)
    }

    /** 401 — API Key mal formada/mal tipeada. No es una revocación. */
    fun notifyCredentialWarning() {
        _credentialWarning.tryEmit(Unit)
    }
}
