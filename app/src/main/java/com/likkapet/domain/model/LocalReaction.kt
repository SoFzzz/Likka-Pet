package com.likkapet.domain.model

/** Local reactions without AI (RF-O12); the phrase comes from the key of the same name in `reactions.json`. */
enum class LocalReaction(
    val key: String,
) {
    DRAG("drag"),
    POKE("poke"),
}
