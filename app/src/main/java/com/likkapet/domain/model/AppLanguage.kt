package com.likkapet.domain.model

import java.util.Locale

/** DataStore-backed choice from Settings (design system §2.5, RF-S09). */
enum class AppLanguage {
    SYSTEM,
    ES,
    EN,
    ;

    /** Resolves the enum to a concrete language tag ("es" or "en"). */
    fun resolve(systemLanguage: String = Locale.getDefault().language): String =
        when (this) {
            ES -> "es"
            EN -> "en"
            SYSTEM -> if (systemLanguage.lowercase().startsWith("en")) "en" else "es"
        }
}
