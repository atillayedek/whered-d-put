package com.wheredidiputit.presentation.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.theme.WdipiShapes
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing
import com.wheredidiputit.domain.model.Announcement

/** A short message from the team; stays until the person closes it. */
@Composable
fun AnnouncementBanner(announcement: Announcement, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
    Surface(
        shape = WdipiShapes.card,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = WdipiSpacing.lg, top = WdipiSpacing.xs, bottom = WdipiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Campaign, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(WdipiSpacing.md))
            Text(
                text = announcement.messageFor(locale?.language.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = WdipiSpacing.sm),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.announcement_dismiss))
            }
        }
    }
}
