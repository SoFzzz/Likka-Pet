package com.likkapet.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.likkapet.domain.model.ThemeMode

val LikkaDarkColorScheme =
    darkColorScheme(
        primary = LikkaColors.RaspberryLight,
        onPrimary = LikkaColors.Cocoa,
        primaryContainer = LikkaColors.Ochre,
        onPrimaryContainer = LikkaColors.Cocoa,
        secondary = LikkaColors.Ochre,
        onSecondary = LikkaColors.Cocoa,
        // secondaryContainer: SegmentedButton's selected-segment fill (design system §2.5), so it
        // needs its own AA-verified pair, not Material's default (purple) tonal derivation.
        secondaryContainer = LikkaColors.SurfaceHigh,
        onSecondaryContainer = LikkaColors.Cream,
        tertiary = LikkaColors.RaspberryLight,
        onTertiary = LikkaColors.Cocoa,
        // tertiaryContainer mirrors errorContainer/onErrorContainer: same raspberry semantics.
        tertiaryContainer = LikkaColors.Raspberry,
        onTertiaryContainer = LikkaColors.Cream,
        // RaspberryLight (not the pure Raspberry fill) so text/icons on the container stay AA (§1.1).
        error = LikkaColors.RaspberryLight,
        onError = LikkaColors.Cocoa,
        errorContainer = LikkaColors.Raspberry,
        onErrorContainer = LikkaColors.Cream,
        background = LikkaColors.Cocoa,
        onBackground = LikkaColors.Cream,
        surface = LikkaColors.Plum,
        onSurface = LikkaColors.Cream,
        surfaceVariant = LikkaColors.SurfaceHigh,
        onSurfaceVariant = LikkaColors.CreamMuted,
        surfaceDim = LikkaColors.SurfaceDim,
        surfaceBright = LikkaColors.SurfaceBright,
        surfaceContainerLowest = LikkaColors.SurfaceContainerLowest,
        surfaceContainerLow = LikkaColors.SurfaceContainerLow,
        surfaceContainer = LikkaColors.SurfaceContainer,
        surfaceContainerHigh = LikkaColors.SurfaceContainerHigh,
        surfaceContainerHighest = LikkaColors.SurfaceContainerHighest,
        inverseSurface = LikkaColors.InverseSurface,
        inverseOnSurface = LikkaColors.InverseOnSurface,
        outline = LikkaColors.Outline,
        outlineVariant = LikkaColors.SurfaceHigh,
        scrim = LikkaColors.Scrim,
    )

// Every role the dark scheme defines is set here too, so Material 3 never falls back to its own
// purples. Only used by app screens; the overlay always applies LikkaDarkColorScheme (§1.1).
val LikkaLightColorScheme =
    lightColorScheme(
        // RaspberryMid, not the light RaspberryLight button fill: colorScheme.primary is also used as
        // TextButton/OutlinedButton text, the checked Switch track, checkbox fill and the focused
        // field border — #FCA30B only reaches 1.86:1/2.02:1 there (§1.1). The filled Primary
        // button keeps #FCA30B via LikkaButtonColors, which does not read colorScheme.
        primary = LikkaColors.RaspberryMid,
        onPrimary = LikkaColors.LightSurface,
        primaryContainer = LikkaColors.Ochre,
        onPrimaryContainer = LikkaColors.Cocoa,
        // OchreDark, not brand.ochre (#D17B0F): colorScheme.secondary is also used as text/icon
        // color in some components, and #D17B0F doesn't clear AA there either (§1.1).
        secondary = LikkaColors.OchreDark,
        onSecondary = LikkaColors.LightSurface,
        secondaryContainer = LikkaColors.LightSurfaceVariant,
        onSecondaryContainer = LikkaColors.RaspberryMid,
        tertiary = LikkaColors.Raspberry,
        onTertiary = LikkaColors.LightBackground,
        tertiaryContainer = LikkaColors.LightSurfaceVariant,
        onTertiaryContainer = LikkaColors.Raspberry,
        // Pure Raspberry text/borders are AA-compliant in light (§1.1), unlike in dark.
        error = LikkaColors.Raspberry,
        onError = LikkaColors.LightBackground,
        errorContainer = LikkaColors.LightSurfaceVariant,
        onErrorContainer = LikkaColors.Raspberry,
        background = LikkaColors.LightBackground,
        onBackground = LikkaColors.Cocoa,
        surface = LikkaColors.LightSurface,
        onSurface = LikkaColors.Cocoa,
        surfaceVariant = LikkaColors.LightSurfaceVariant,
        onSurfaceVariant = LikkaColors.LightOnSurfaceVariant,
        surfaceDim = LikkaColors.LightSurfaceVariant,
        surfaceBright = LikkaColors.LightSurface,
        surfaceContainerLowest = LikkaColors.LightSurface,
        surfaceContainerLow = LikkaColors.LightSurface,
        surfaceContainer = LikkaColors.LightSurfaceVariant,
        surfaceContainerHigh = LikkaColors.LightSurfaceVariant,
        surfaceContainerHighest = LikkaColors.LightSurfaceVariant,
        inverseSurface = LikkaColors.Cocoa,
        inverseOnSurface = LikkaColors.LightBackground,
        outline = LikkaColors.Outline,
        outlineVariant = LikkaColors.LightSurfaceVariant,
        scrim = LikkaColors.Scrim,
    )

// ThemeMode lives in domain/model (pure Kotlin) because data/ needs it and cannot depend on
// presentation/ (documentación §10.1); this function only resolves it to a ColorScheme.
@Composable
fun LikkaTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val isLight =
        when (themeMode) {
            ThemeMode.LIGHT -> true
            ThemeMode.DARK -> false
            ThemeMode.SYSTEM -> !isSystemInDarkTheme()
        }
    CompositionLocalProvider(
        LocalLikkaIsLightTheme provides isLight,
        LocalLikkaButtonColors provides LikkaDefaultButtonColors,
        LocalLikkaExtrasColors provides LikkaDefaultExtrasColors,
    ) {
        // No dynamic color: Material You is never wired in, so the scheme is always Likka's palette.
        MaterialTheme(
            colorScheme = if (isLight) LikkaLightColorScheme else LikkaDarkColorScheme,
            typography = LikkaMaterialTypography,
            shapes = LikkaMaterialShapes,
            content = content,
        )
    }
}
