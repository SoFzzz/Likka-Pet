package com.likkapet.domain.model

/** DataStore-backed choice from Settings (design system §2.5); SYSTEM is resolved to light/dark in presentation (`LikkaTheme`, `isSystemInDarkTheme()`). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
