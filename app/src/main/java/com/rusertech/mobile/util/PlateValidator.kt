package com.rusertech.mobile.util

/**
 * Valida patentes de toda LATAM.
 * Acepta 6-8 caracteres alfanuméricos tras eliminar separadores.
 * Mercosur AR/BR/UY/PY (AB123CD, 7), clásica AR (ABC123, 6),
 * BR pre-Mercosur (ABC1234, 7), CL (ABCD12, 6), CO (ABC123, 6),
 * MX (ABC1234, 7), PE (A1B234, 6), BO (1234ABC, 7), EC (ABC1234, 7).
 */
object PlateValidator {
    private const val MIN = 6
    private const val MAX = 8

    fun normalize(input: String): String = input.trim().uppercase().filter { it.isLetterOrDigit() }

    fun isValid(input: String): Boolean = normalize(input).length in MIN..MAX

    fun errorOrNull(input: String): String? {
        val n = normalize(input)
        return when {
            n.isEmpty() -> "La patente es obligatoria"
            n.length < MIN -> "Mínimo $MIN caracteres"
            n.length > MAX -> "Máximo $MAX caracteres"
            else -> null
        }
    }
}
