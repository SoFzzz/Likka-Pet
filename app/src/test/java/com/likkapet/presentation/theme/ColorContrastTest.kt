package com.likkapet.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Checks the text/background pairs of design system §1.1 (dark and light) against WCAG 2.1
 * contrast ratios, so a palette change that breaks AA fails the build.
 */
class ColorContrastTest {
    private data class ContrastPair(
        val name: String,
        val foreground: Color,
        val background: Color,
        val minRatio: Double,
    )

    @Test
    fun contrastRatio_matchesWcagReferenceValues() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), RATIO_TOLERANCE)
        assertEquals(1.0, contrastRatio(LikkaColors.Cocoa, LikkaColors.Cocoa), RATIO_TOLERANCE)
        // Design system §1.1: cream on cocoa = 14.1.
        assertEquals(14.1, contrastRatio(LikkaColors.Cream, LikkaColors.Cocoa), DOC_ROUNDING_TOLERANCE)
    }

    @Test
    fun darkScheme_textAndBorderPairsMeetWcag() {
        assertAllPairsPass(darkPairs(LikkaDarkColorScheme))
    }

    @Test
    fun lightScheme_textAndBorderPairsMeetWcag() {
        assertAllPairsPass(lightPairs(LikkaLightColorScheme))
    }

    @Test
    fun filledButtons_textMeetsWcagInBothThemes() {
        val buttons = LikkaDefaultButtonColors
        assertAllPairsPass(
            listOf(
                ContrastPair("primary button", buttons.onPrimaryContainer, buttons.primaryContainer, AA_NORMAL_TEXT),
                ContrastPair("warning button", buttons.onWarningContainer, buttons.warningContainer, AA_NORMAL_TEXT),
                ContrastPair("danger button", buttons.onDangerContainer, buttons.dangerContainer, AA_NORMAL_TEXT),
            ),
        )
    }

    private fun darkPairs(scheme: ColorScheme): List<ContrastPair> =
        listOf(
            ContrastPair("onBackground on background", scheme.onBackground, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("onSurface on surface", scheme.onSurface, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("onSurface on surfaceVariant", scheme.onSurface, scheme.surfaceVariant, AA_NORMAL_TEXT),
            ContrastPair("onSurfaceVariant on background", scheme.onSurfaceVariant, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("onSurfaceVariant on surface", scheme.onSurfaceVariant, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("primary on background", scheme.primary, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("primary on surface", scheme.primary, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("onPrimary on primary", scheme.onPrimary, scheme.primary, AA_NORMAL_TEXT),
            ContrastPair("secondary on background", scheme.secondary, scheme.background, AA_NORMAL_TEXT),
            // §1.1: ochre on plum is 4.3, allowed only for large text (≥24sp, or ≥18.7sp bold).
            ContrastPair("secondary on surface (large text only)", scheme.secondary, scheme.surface, AA_LARGE_TEXT),
            ContrastPair("onSecondary on secondary", scheme.onSecondary, scheme.secondary, AA_NORMAL_TEXT),
            ContrastPair("onSecondaryContainer on container", scheme.onSecondaryContainer, scheme.secondaryContainer, AA_NORMAL_TEXT),
            ContrastPair("tertiary on background", scheme.tertiary, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("tertiary on surface", scheme.tertiary, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("onTertiaryContainer on container", scheme.onTertiaryContainer, scheme.tertiaryContainer, AA_NORMAL_TEXT),
            ContrastPair("error on background", scheme.error, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("error on surface", scheme.error, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("onErrorContainer on container", scheme.onErrorContainer, scheme.errorContainer, AA_NORMAL_TEXT),
            ContrastPair("outline on background", scheme.outline, scheme.background, AA_NON_TEXT),
            ContrastPair("outline on surface", scheme.outline, scheme.surface, AA_NON_TEXT),
        )

    private fun lightPairs(scheme: ColorScheme): List<ContrastPair> =
        listOf(
            ContrastPair("onBackground on background", scheme.onBackground, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("onSurface on surface", scheme.onSurface, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("onSurfaceVariant on background", scheme.onSurfaceVariant, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("onSurfaceVariant on surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant, AA_NORMAL_TEXT),
            ContrastPair("primary on background", scheme.primary, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("primary on surfaceVariant", scheme.primary, scheme.surfaceVariant, AA_NORMAL_TEXT),
            ContrastPair("primary on surface", scheme.primary, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("onPrimary on primary", scheme.onPrimary, scheme.primary, AA_NORMAL_TEXT),
            ContrastPair("secondary on background", scheme.secondary, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("secondary on surfaceVariant", scheme.secondary, scheme.surfaceVariant, AA_NORMAL_TEXT),
            ContrastPair("secondary on surface", scheme.secondary, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("onSecondary on secondary", scheme.onSecondary, scheme.secondary, AA_NORMAL_TEXT),
            ContrastPair("onSecondaryContainer on container", scheme.onSecondaryContainer, scheme.secondaryContainer, AA_NORMAL_TEXT),
            ContrastPair("tertiary on background", scheme.tertiary, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("tertiary on surface", scheme.tertiary, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("onTertiaryContainer on container", scheme.onTertiaryContainer, scheme.tertiaryContainer, AA_NORMAL_TEXT),
            ContrastPair("error on background", scheme.error, scheme.background, AA_NORMAL_TEXT),
            ContrastPair("error on surface", scheme.error, scheme.surface, AA_NORMAL_TEXT),
            ContrastPair("onErrorContainer on container", scheme.onErrorContainer, scheme.errorContainer, AA_NORMAL_TEXT),
            ContrastPair("outline on background", scheme.outline, scheme.background, AA_NON_TEXT),
            ContrastPair("outline on surface", scheme.outline, scheme.surface, AA_NON_TEXT),
        )

    private fun assertAllPairsPass(pairs: List<ContrastPair>) {
        val failures =
            pairs
                .map { it to contrastRatio(it.foreground, it.background) }
                .filter { (pair, ratio) -> ratio < pair.minRatio }
                .map { (pair, ratio) -> String.format(Locale.ROOT, "%s: %.2f < %.1f", pair.name, ratio, pair.minRatio) }
        assertTrue("Pairs below WCAG minimum:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** WCAG 2.1 contrast ratio: (L1 + 0.05) / (L2 + 0.05), L1 being the lighter luminance. */
    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private companion object {
        const val AA_NORMAL_TEXT = 4.5
        const val AA_LARGE_TEXT = 3.0
        const val AA_NON_TEXT = 3.0 // WCAG 1.4.11, component boundaries such as outlines
        const val RATIO_TOLERANCE = 0.001
        const val DOC_ROUNDING_TOLERANCE = 0.05
    }
}
