package com.likkapet.domain.model

/** A local reaction on screen (RF-O12): which gesture caused it and its phrase. */
data class ShownReaction(
    val reaction: LocalReaction,
    val text: String,
)
