package com.likkapet.domain.port

import com.likkapet.domain.model.LocalReaction

/** Local phrases for the gestures on Likka (RF-O12): read from `assets/reactions.json`, never from the network. */
fun interface ReactionPhrases {
    fun next(reaction: LocalReaction): String
}
