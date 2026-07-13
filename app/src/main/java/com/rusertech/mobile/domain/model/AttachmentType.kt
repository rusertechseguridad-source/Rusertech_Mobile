package com.rusertech.mobile.domain.model

enum class AttachmentType(val code: String, val displayName: String) {
    CARGO_START("CARGO_START", "Carga — Inicio"),
    CARGO_END("CARGO_END", "Carga — Entrega"),
    INCIDENT("INCIDENT", "Incidente"),
    OTHER("OTHER", "Otro")
}
