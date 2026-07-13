package com.rusertech.mobile.util

/**
 * Valida documentos de identidad de toda LATAM.
 * Acepta 6-20 caracteres alfanuméricos tras eliminar separadores.
 * AR DNI (7-8 dígitos), BR CPF (11 dígitos), CL RUT (7-9+DV),
 * CO Cédula (8-10), MX CURP (18), PE DNI (8), UY CI (7-8), VE Cédula (7-9).
 */
object IdentityValidator {
    private const val MIN = 6
    private const val MAX = 20

    fun normalize(input: String): String = input.trim().uppercase().filter { it.isLetterOrDigit() }

    fun isValid(input: String): Boolean = normalize(input).length in MIN..MAX

    fun errorOrNull(input: String): String? {
        val n = normalize(input)
        return when {
            n.isEmpty() -> "El documento es obligatorio"
            n.length < MIN -> "Mínimo $MIN caracteres"
            n.length > MAX -> "Máximo $MAX caracteres"
            else -> null
        }
    }
}
