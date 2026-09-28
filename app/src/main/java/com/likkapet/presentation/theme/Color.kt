package com.likkapet.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

object LikkaColors {
    // Forest palette (brand)
    val Amber = Color(0xFFFCA30B)
    val Ochre = Color(0xFFD17B0F)
    val Raspberry = Color(0xFFA2315D)
    val Plum = Color(0xFF4B1C33)
    val Cocoa = Color(0xFF3D1B1C)

    // Support colors
    val Cream = Color(0xFFFFF4E6)
    val CreamMuted = Color(0xFFE3C9C4)
    val SurfaceHigh = Color(0xFF5E2A45)
    val SwitchTrackInactive = Color(0xFF8A6B78)
    val Outline = Color(0xFF9C7488) // v3: was #6B3552 (1.64:1); now ≥3:1 (WCAG 1.4.11)
    val RaspberryLight = Color(0xFFF07AA0)

    // Extra surface/error roles so Material 3 doesn't fall back to its own purples
    // for tokens the v2 scheme left unset (surfaceContainer*, inverseSurface, scrim...).
    val SurfaceDim = Color(0xFF2E1416)
    val SurfaceBright = Color(0xFF6B3552)
    val SurfaceContainerLowest = Color(0xFF260F11)
    val SurfaceContainerLow = Color(0xFF351719)
    val SurfaceContainer = Color(0xFF3D1B1C)
    val SurfaceContainerHigh = Color(0xFF48222A)
    val SurfaceContainerHighest = Color(0xFF5E2A45)
    val InverseSurface = Color(0xFFE3C9C4)
    val InverseOnSurface = Color(0xFF3D1B1C)
    val Scrim = Color(0xFF000000)

    // Phase 4b: the overlay's foot shadow (design system §1.7), translucent black under Likka's feet.
    val FootShadow = Color(0x59000000)

    // Light theme (design system v3.6, COULD, §1.1 "Paleta clara"). App screens only — the
    // overlay always uses the dark tokens above. Cocoa/Raspberry/Outline are reused as-is: the
    // light palette was designed so the main text and the level-3/rest border colors match the
    // existing brand hex values, verified in §1.1.
    val LightBackground = Color(0xFFFFF4E6)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF7E6DC)
    val LightOnSurfaceVariant = Color(0xFF6B4A55)
    val AmberDark = Color(0xFF8F5600) // level 1 border / amber text accent in light; also colorScheme.primary in light
    val OchreDark = Color(0xFF9A4A00) // level 2 border / ochre text accent in light
    val RaspberryMid = Color(0xFFAA3C66) // light-theme primary: text, icons, switch track (AA on background and surfaceVariant)
}

/**
 * True in the light theme, false in dark; set by [LikkaTheme]. The overlay always sees `false`,
 * since it always renders inside `LikkaTheme(ThemeMode.DARK)` (documentación §9.1).
 */
val LocalLikkaIsLightTheme = compositionLocalOf { false }

/**
 * Likka halo (overlay levels 1–2), stage border (dashboard, onboarding, level 3) and bubble color
 * for an escalation level (0 = calm); reads [LocalLikkaIsLightTheme] instead of taking a
 * parameter, so callers can't forget to pass the active theme.
 */
@Composable
fun levelColor(level: Int): Color =
    if (LocalLikkaIsLightTheme.current) {
        when (level) {
            1, 2 -> LikkaColors.RaspberryMid
            3 -> LikkaColors.Raspberry
            else -> LikkaColors.Outline
        }
    } else {
        when (level) {
            1, 2, 3 -> LikkaColors.RaspberryLight
            else -> LikkaColors.Cream
        }
    }

/**
 * Fill of Likka's cream stage circle (dashboard, onboarding, level 3 panel): `neutral.cream` in
 * dark, `#FFFFFF` in light (design system §1.1 "Paleta clara", regla 3, and §1.7).
 */
@Composable
fun stageFillColor(): Color = if (LocalLikkaIsLightTheme.current) LikkaColors.LightSurface else LikkaColors.Cream

/**
 * Filled-button colors (design system §1.8): Primary/Advertencia/Peligro keep the same brand fill
 * in both themes, unlike `colorScheme.primary`/`secondary`/`tertiary` (which change in light so
 * they stay usable as text/border colors, §1.1). Read via [LocalLikkaButtonColors], never
 * `LikkaColors.X` directly, so the source of these colors stays swappable and testable like any
 * other themed token.
 */
data class LikkaButtonColors(
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
)

val LikkaDefaultButtonColors =
    LikkaButtonColors(
        primaryContainer = LikkaColors.RaspberryLight,
        onPrimaryContainer = LikkaColors.Cocoa,
        warningContainer = LikkaColors.Ochre,
        onWarningContainer = LikkaColors.Cocoa,
        dangerContainer = LikkaColors.Raspberry,
        onDangerContainer = LikkaColors.Cream,
    )

val LocalLikkaButtonColors = compositionLocalOf { LikkaDefaultButtonColors }

/**
 * Colors of the Compose extras around Likka (design system §1.7: `sparkles`, `z`, `sweat_drop`,
 * `aura` and the foot shadow; the halo uses [levelColor]). The same in both themes, like
 * [LikkaButtonColors]: those extras are drawn on the stage circle, cream or white, and in the overlay,
 * which is always dark. The cocoa outline is the 1 px outline the sprite itself has, so a sparkle or
 * a drop reads on any of those backgrounds. Read via [LocalLikkaExtrasColors].
 */
data class LikkaExtrasColors(
    val outline: Color,
    val sparkle: Color,
    val sleepZ: Color,
    val sweatDrop: Color,
    val sweatDropShine: Color,
    // The aura's gradient, raspberry next to Likka, light raspberry outside (design system §1.6)
    val auraInner: Color,
    val auraOuter: Color,
    val footShadow: Color,
)

val LikkaDefaultExtrasColors =
    LikkaExtrasColors(
        outline = LikkaColors.Cocoa,
        sparkle = LikkaColors.Amber,
        sleepZ = LikkaColors.Plum,
        sweatDrop = LikkaColors.Cream,
        sweatDropShine = LikkaColors.LightSurface,
        auraInner = LikkaColors.Raspberry,
        auraOuter = LikkaColors.RaspberryLight,
        footShadow = LikkaColors.FootShadow,
    )

val LocalLikkaExtrasColors = compositionLocalOf { LikkaDefaultExtrasColors }
