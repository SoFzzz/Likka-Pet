package com.likkapet.domain.model

/** Escalation level of a single reason track (documentación §3.3); NONE is "Sin aviso". */
enum class ReasonLevel(
    val value: Int,
) {
    NONE(0),
    LEVEL_1(1),
    LEVEL_2(2),
    LEVEL_3(3),
}
