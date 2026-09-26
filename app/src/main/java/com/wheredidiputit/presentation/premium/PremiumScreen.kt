package com.wheredidiputit.presentation.premium

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheredidiputit.R
import com.wheredidiputit.core.config.AppConfig
import com.wheredidiputit.core.designsystem.component.NavIcon
import com.wheredidiputit.core.designsystem.component.PrimaryButton
import com.wheredidiputit.core.designsystem.component.QuietButton
import com.wheredidiputit.core.designsystem.component.WdipiTopBar
import com.wheredidiputit.core.designsystem.theme.WdipiShapes
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing
import com.wheredidiputit.domain.model.FreePlan
import com.wheredidiputit.domain.model.PremiumOffer
import com.wheredidiputit.domain.model.PurchaseOutcome
import com.wheredidiputit.presentation.common.AppSnackbarHost
import com.wheredidiputit.presentation.common.LocalSnackbarHostState

@Composable
fun PremiumScreen(
    onClose: () -> Unit,
    onPurchased: () -> Unit,
    viewModel: PremiumViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        viewModel.outcomes.collect { outcome ->
            when (outcome) {
                PurchaseOutcome.PURCHASED -> onPurchased()
                PurchaseOutcome.PENDING -> snackbarHostState.showSnackbar(context.getString(R.string.premium_pending))
                PurchaseOutcome.FAILED -> snackbarHostState.showSnackbar(context.getString(R.string.premium_failed))
                PurchaseOutcome.CANCELLED -> Unit
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { WdipiTopBar(title = stringResource(R.string.premium_title), onNavigate = onClose, navIcon = NavIcon.CLOSE) },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WdipiSpacing.screen, vertical = WdipiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WdipiSpacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }

            if (state.premium.isPremium) {
                Text(
                    stringResource(R.string.premium_active_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.premium_active_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QuietButton(
                    text = stringResource(R.string.premium_manage),
                    onClick = { uriHandler.openUri(AppConfig.manageSubscriptionUrl(context.packageName)) },
                )
                return@Column
            }

            Text(
                stringResource(R.string.premium_headline),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(
                    if (state.limitReached) R.string.premium_limit_message else R.string.premium_intro,
                    FreePlan.ITEM_LIMIT,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                shape = WdipiShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(WdipiSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(WdipiSpacing.md),
                ) {
                    Benefit(stringResource(R.string.premium_benefit_unlimited))
                    Benefit(stringResource(R.string.premium_benefit_no_ads))
                    Text(
                        stringResource(R.string.premium_usage, state.itemCount.coerceAtMost(FreePlan.ITEM_LIMIT), FreePlan.ITEM_LIMIT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.premium.offer?.let { offer ->
                Text(priceLabel(offer), style = MaterialTheme.typography.titleLarge)
            }

            if (state.premium.isPending) {
                Text(
                    stringResource(R.string.premium_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.premium.storeAvailable) {
                PrimaryButton(
                    text = stringResource(R.string.premium_subscribe),
                    onClick = {
                        val activity = context.findActivity()
                        if (activity == null || !viewModel.subscribe(activity)) {
                            viewModel.retry()
                        }
                    },
                    enabled = state.canPurchase,
                    loading = state.isPurchasing,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    stringResource(R.string.premium_store_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QuietButton(text = stringResource(R.string.action_try_again), onClick = viewModel::retry)
            }

            Text(
                stringResource(R.string.premium_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.limitReached) {
                QuietButton(text = stringResource(R.string.premium_delete_instead), onClick = onClose)
            }
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(WdipiSpacing.md))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun priceLabel(offer: PremiumOffer): String = when (offer.billingPeriod) {
    "P1M" -> stringResource(R.string.premium_price_month, offer.formattedPrice)
    "P1Y" -> stringResource(R.string.premium_price_year, offer.formattedPrice)
    "P1W" -> stringResource(R.string.premium_price_week, offer.formattedPrice)
    else -> offer.formattedPrice
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
