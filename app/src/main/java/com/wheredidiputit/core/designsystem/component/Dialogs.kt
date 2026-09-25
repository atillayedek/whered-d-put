package com.wheredidiputit.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            QuietButton(text = confirmLabel, onClick = onConfirm, destructive = destructive, enabled = confirmEnabled)
        },
        dismissButton = { QuietButton(text = dismissLabel, onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}
