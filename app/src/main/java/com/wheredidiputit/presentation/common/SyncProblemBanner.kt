package com.wheredidiputit.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.component.QuietButton
import com.wheredidiputit.core.designsystem.theme.WdipiShapes
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing

/** Reassures the person that nothing was lost when the cloud rejected a sync. */
@Composable
fun SyncProblemBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = WdipiShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = WdipiSpacing.lg, top = WdipiSpacing.md, bottom = WdipiSpacing.xs, end = WdipiSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Spacer(Modifier.width(WdipiSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sync_failed_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.sync_failed_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QuietButton(text = stringResource(R.string.action_try_again), onClick = onRetry)
            }
        }
    }
}
