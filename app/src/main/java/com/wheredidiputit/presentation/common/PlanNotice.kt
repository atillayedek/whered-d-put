package com.wheredidiputit.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
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
import com.wheredidiputit.domain.model.FreePlan

/** Shown on the free plan near its limit: how many memories are left, and the way out. */
@Composable
fun PlanNotice(itemCount: Int, onOpenPremium: () -> Unit, modifier: Modifier = Modifier) {
    val full = itemCount >= FreePlan.ITEM_LIMIT
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
                imageVector = Icons.Outlined.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Spacer(Modifier.width(WdipiSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.plan_notice_title, itemCount.coerceAtMost(FreePlan.ITEM_LIMIT), FreePlan.ITEM_LIMIT),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(if (full) R.string.plan_notice_full else R.string.plan_notice_almost),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QuietButton(text = stringResource(R.string.plan_notice_action), onClick = onOpenPremium)
            }
        }
    }
}
