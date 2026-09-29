package com.likkapet.domain.model

/**
 * Outcome of a pause request (rule E, documentación §3.2). The coordinator does not persist the
 * daily count: the caller must increment `pauses_today` only when the result is [ACCEPTED].
 */
enum class PauseResult {
    ACCEPTED,
    REJECTED_DAILY_LIMIT,
    REJECTED_LEVEL_3,
    REJECTED_WHILE_EJECTED,
    REJECTED_ALREADY_PAUSED,
}
