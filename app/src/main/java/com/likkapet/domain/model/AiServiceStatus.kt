package com.likkapet.domain.model

/**
 * What the last answers of the Worker say about the AI service (design system §3.5 "Sin internet /
 * saldo"). Optimistic until a request proves otherwise: no request has failed yet.
 */
data class AiServiceStatus(
    val isOnline: Boolean = true,
    val hasCredit: Boolean = true,
)
