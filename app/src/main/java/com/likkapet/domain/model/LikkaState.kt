package com.likkapet.domain.model

/**
 * Global state of the machine in documentación §3.3. Leaving a watched app in Level 1–2 is not a
 * state of its own: it stays WATCHING and the overlay reports `OverlayVisibility.HIDDEN_WHILE_AWAY`.
 */
enum class LikkaState { IDLE, WATCHING, EJECTED, SUSPENDED, PAUSED }
