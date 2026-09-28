package com.likkapet.presentation.theme

import androidx.annotation.FontRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.likkapet.R

// Baloo 2 and Nunito (design system §1.2) are variable fonts from github.com/google/fonts under
// SIL OFL 1.1 (license texts in assets/licenses/). A variable file renders at its default weight
// unless each Font sets the wght axis, so every weight used below is declared explicitly.
val Baloo2 =
    FontFamily(
        variableFont(R.font.baloo2_variable, FontWeight.SemiBold),
        variableFont(R.font.baloo2_variable, FontWeight.Bold),
        variableFont(R.font.baloo2_variable, FontWeight.ExtraBold),
    )

val Nunito =
    FontFamily(
        variableFont(R.font.nunito_variable, FontWeight.Normal),
        variableFont(R.font.nunito_variable, FontWeight.SemiBold),
        variableFont(R.font.nunito_variable, FontWeight.Bold),
    )

// The Font overload that takes variationSettings is still @ExperimentalTextApi in Compose 1.11.
@OptIn(ExperimentalTextApi::class)
private fun variableFont(
    @FontRes resId: Int,
    weight: FontWeight,
): Font =
    Font(
        resId = resId,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

object LikkaTypography {
    val display = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Baloo2)
    val headline = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, fontFamily = Baloo2)
    val title = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = Baloo2)
    val bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, fontFamily = Nunito)
    val bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, fontFamily = Nunito)
    val label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Nunito)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = Nunito)
}

// Maps to Material 3's Typography so default components (Text, Button...) pick up the right style.
val LikkaMaterialTypography =
    Typography(
        displayLarge = LikkaTypography.display,
        headlineLarge = LikkaTypography.headline,
        titleLarge = LikkaTypography.title,
        bodyLarge = LikkaTypography.bodyLarge,
        bodyMedium = LikkaTypography.bodyMedium,
        labelLarge = LikkaTypography.label,
        labelSmall = LikkaTypography.caption,
    )
