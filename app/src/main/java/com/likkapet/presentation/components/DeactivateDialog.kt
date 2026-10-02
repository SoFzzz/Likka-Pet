package com.likkapet.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.likkapet.R

/** Confirmation before switching Likka off (RF-S04), shared by the dashboard and Settings. */
@Composable
fun DeactivateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.deactivate_dialog_title), style = MaterialTheme.typography.titleLarge) },
        text = { Text(stringResource(R.string.deactivate_dialog_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.deactivate_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@LikkaThemePreviews
@Composable
private fun DeactivateDialogPreview() {
    LikkaPreview { DeactivateDialog(onConfirm = {}, onDismiss = {}) }
}
