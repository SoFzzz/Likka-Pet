package com.likkapet.presentation.components

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.likkapet.domain.model.ThemeMode
import com.likkapet.presentation.theme.LikkaTheme

/** One preview per theme: dark and light (design system §1.1). Pair with [LikkaPreview]. */
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
annotation class LikkaThemePreviews

/** Follows the preview's night mode through [ThemeMode.SYSTEM], so a single body serves both themes. */
@Composable
fun LikkaPreview(content: @Composable () -> Unit) {
    LikkaTheme(ThemeMode.SYSTEM) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

// Design reference width (design system §1.3) and a tall phone.
private const val PREVIEW_WIDTH_DP = 360
private const val PREVIEW_HEIGHT_DP = 760
