package com.likkapet.presentation.servicecontrol

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.likkapet.R
import com.likkapet.domain.model.ThemeMode
import com.likkapet.presentation.theme.LikkaSpacing
import com.likkapet.presentation.theme.LikkaTheme
import com.likkapet.presentation.theme.LocalLikkaButtonColors

/**
 * Temporary spike screen (documentación §14) to start and stop LikkaService until the dashboard
 * exists. Stateless on purpose: it has no ViewModel because it shows no service state.
 */
@Composable
fun ServiceControlContent(
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val buttonColors = LocalLikkaButtonColors.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(LikkaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            // Primary and Danger variants (design system §1.8).
            FilledButton(
                textRes = R.string.service_start_button,
                containerColor = buttonColors.primaryContainer,
                contentColor = buttonColors.onPrimaryContainer,
                onClick = onStartClick,
            )
            FilledButton(
                textRes = R.string.service_stop_button,
                containerColor = buttonColors.dangerContainer,
                contentColor = buttonColors.onDangerContainer,
                onClick = onStopClick,
            )
        }
    }
}

@Composable
private fun FilledButton(
    @StringRes textRes: Int,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Text(text = stringResource(textRes))
    }
}

@Preview(name = "Dark")
@Composable
private fun ServiceControlContentDarkPreview() {
    LikkaTheme(ThemeMode.DARK) { ServiceControlContent(onStartClick = {}, onStopClick = {}) }
}

@Preview(name = "Light")
@Composable
private fun ServiceControlContentLightPreview() {
    LikkaTheme(ThemeMode.LIGHT) { ServiceControlContent(onStartClick = {}, onStopClick = {}) }
}
