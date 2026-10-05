package com.likkapet.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.likkapet.R
import com.likkapet.presentation.components.LikkaButton
import com.likkapet.presentation.components.LikkaButtonVariant
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.LikkaTopBar
import com.likkapet.presentation.components.SettingsGroupHeader
import com.likkapet.presentation.components.screenInsets
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaShapes
import com.likkapet.presentation.theme.LikkaSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fonts credited on the About screen (design system §3.1), with their license text in `assets/`. */
enum class FontLicense(
    val nameRes: Int,
    val assetPath: String,
) {
    BALOO_2(R.string.about_font_baloo2, "licenses/baloo2_OFL.txt"),
    NUNITO(R.string.about_font_nunito, "licenses/nunito_OFL.txt"),
}

@Immutable
data class AboutUiState(
    val versionName: String,
    val openLicense: FontLicense? = null,
    val licenseText: String = "",
)

class AboutViewModel(
    versionName: String,
    private val readAsset: (String) -> String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AboutUiState(versionName))
    val uiState: StateFlow<AboutUiState> = mutableState.asStateFlow()

    fun onLicenseClick(license: FontLicense) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { readAsset(license.assetPath) }
            mutableState.value = mutableState.value.copy(openLicense = license, licenseText = text)
        }
    }

    fun onLicenseDismiss() {
        mutableState.value = mutableState.value.copy(openLicense = null, licenseText = "")
    }
}

@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onBackClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AboutContent(
        state = state,
        onBackClick = onBackClick,
        onLicenseClick = viewModel::onLicenseClick,
        onLicenseDismiss = viewModel::onLicenseDismiss,
    )
}

@Composable
fun AboutContent(
    state: AboutUiState,
    onBackClick: () -> Unit,
    onLicenseClick: (FontLicense) -> Unit,
    onLicenseDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.screenInsets()) {
        LikkaTopBar(title = stringResource(R.string.about_title), onBackClick = onBackClick)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LikkaSpacing.l),
            verticalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
        ) {
            Text(
                text = stringResource(R.string.about_author),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.about_course),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.about_version, state.versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsGroupHeader(stringResource(R.string.about_fonts_header))
            FontLicense.entries.forEach { license ->
                FontCreditCard(license = license, onLicenseClick = { onLicenseClick(license) })
            }
        }
    }
    state.openLicense?.let { LicenseDialog(license = it, text = state.licenseText, onDismiss = onLicenseDismiss) }
}

@Composable
private fun FontCreditCard(
    license: FontLicense,
    onLicenseClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LikkaShapes.m,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(LikkaComponentSize.borderThin, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(LikkaSpacing.m), verticalArrangement = Arrangement.spacedBy(LikkaSpacing.xs)) {
            Text(
                text = stringResource(license.nameRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.about_font_credit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LikkaButton(
                text = stringResource(R.string.about_license_button),
                onClick = onLicenseClick,
                variant = LikkaButtonVariant.TEXT,
                fillWidth = false,
            )
        }
    }
}

@Composable
private fun LicenseDialog(
    license: FontLicense,
    text: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(license.nameRes), style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@LikkaThemePreviews
@Composable
private fun AboutContentPreview() {
    LikkaPreview {
        AboutContent(
            state = AboutUiState(versionName = "0.1.0"),
            onBackClick = {},
            onLicenseClick = {},
            onLicenseDismiss = {},
        )
    }
}
