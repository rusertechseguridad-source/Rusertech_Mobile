package com.rusertech.mobile.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Sub-tipo legible de un evento a partir de su metadata JSON: la primera
 * presente de las claves que la UI captura (categoria del incidente, tipo de
 * checkpoint, lugar de la parada sanitaria), con inicial mayúscula.
 * Null si no hay metadata útil — el llamador muestra solo el tipo.
 *
 * Lo comparten el historial de eventos y el popup del mapa: una sola
 * definición de qué claves son "el sub-tipo".
 */
fun eventSubtype(metadataJson: String): String? = runCatching {
    val obj = Json.parseToJsonElement(metadataJson).jsonObject
    listOf("categoria", "tipo", "lugar", "referencia").firstNotNullOfOrNull { key ->
        obj[key]?.jsonPrimitive?.content
    }?.replaceFirstChar(Char::uppercase)
}.getOrNull()
