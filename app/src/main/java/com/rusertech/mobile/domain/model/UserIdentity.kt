package com.rusertech.mobile.domain.model

/**
 * Identifica a un conductor en cualquier país de LATAM.
 * documentId: DNI (AR), CPF (BR), RUT (CL), Cédula (CO), INE (MX), etc. 6-20 chars alfanuméricos.
 * plate: Patente del vehículo. 6-8 chars. Mapea a vehicles.plate y al campo Asset del HUB.
 * avlUserCode: Código del avl_user asignado a esta flota mobile. Provisto por el operador.
 * apiKey: API Key del avl_user mobile. Provista por el operador.
 */
data class UserIdentity(
    val documentId: String,
    val plate: String,
    val activationCode: String = "",
    val avlUserCode: String = "",
    val apiKey: String = ""
)
