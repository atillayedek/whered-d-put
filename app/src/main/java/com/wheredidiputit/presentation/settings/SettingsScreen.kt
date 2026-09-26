package com.wheredidiputit.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.component.ConfirmDialog
import com.wheredidiputit.core.designsystem.theme.WdipiShapes
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.ThemeMode
import com.wheredidiputit.presentation.common.AppSnackbarHost
import com.wheredidiputit.presentation.common.LanguageSelector
import com.wheredidiputit.presentation.common.LocalAdsController
import com.wheredidiputit.presentation.common.LocalSnackbarHostState
import com.wheredidiputit.presentation.common.SectionLabel
import com.wheredidiputit.presentation.common.TopLevelScreenInsets

@Composable
fun SettingsScreen(
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val ads = LocalAdsController.current
    val adPrivacyRequired by ads.privacyOptionsRequired.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.errors.collect { error ->
            val text = if (error == AppError.NETWORK) R.string.delete_account_offline else R.string.delete_account_failed
            snackbarHostState.showSnackbar(context.getString(text))
        }
    }

    Scaffold(
        contentWindowInsets = TopLevelScreenInsets,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WdipiSpacing.screen, vertical = WdipiSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(WdipiSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(bottom = WdipiSpacing.md)
                    .semantics { heading() },
            )

            SectionLabel(stringResource(R.string.settings_account))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Outlined.Mail,
                    title = stringResource(R.string.settings_email),
                    subtitle = state.email,
                )
            }

            Spacer(Modifier.padding(top = WdipiSpacing.sm))
            SectionLabel(stringResource(R.string.settings_theme))
            ThemeSelector(selected = state.themeMode, onSelect = viewModel::setTheme)

            Spacer(Modifier.padding(top = WdipiSpacing.sm))
            SectionLabel(stringResource(R.string.settings_language))
            LanguageSelector()

            Spacer(Modifier.padding(top = WdipiSpacing.sm))
            SectionLabel(stringResource(R.string.settings_privacy_section))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Outlined.Shield,
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.settings_privacy_subtitle),
                    onClick = onOpenPrivacy,
                    showChevron = true,
                )
                if (adPrivacyRequired) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Outlined.Tune,
                        title = stringResource(R.string.settings_ad_privacy),
                        subtitle = stringResource(R.string.settings_ad_privacy_subtitle),
                        onClick = ads::openPrivacyOptions,
                        showChevron = true,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = stringResource(R.string.settings_sign_out),
                    onClick = viewModel::requestSignOut,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Outlined.DeleteForever,
                    title = stringResource(R.string.settings_delete_account),
                    onClick = viewModel::requestDeleteAccount,
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = stringResource(R.string.settings_version, state.versionLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = WdipiSpacing.xl),
            )
        }
    }

    when (val dialog = state.dialog) {
        is SettingsDialog.SignOut -> ConfirmDialog(
            title = stringResource(R.string.sign_out_title),
            message = if (dialog.unsyncedCount > 0) {
                pluralStringResource(R.plurals.sign_out_unsynced_message, dialog.unsyncedCount, dialog.unsyncedCount)
            } else {
                stringResource(R.string.sign_out_message)
            },
            confirmLabel = stringResource(R.string.settings_sign_out),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = dialog.unsyncedCount > 0,
            confirmEnabled = !state.isWorking,
            onConfirm = viewModel::confirmSignOut,
            onDismiss = viewModel::dismissDialog,
        )
        SettingsDialog.DeleteAccount -> ConfirmDialog(
            title = stringResource(R.string.delete_account_title),
            message = stringResource(R.string.delete_account_message),
            confirmLabel = stringResource(if (state.isWorking) R.string.delete_account_working else R.string.settings_delete_account),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            confirmEnabled = !state.isWorking,
            onConfirm = viewModel::confirmDeleteAccount,
            onDismiss = viewModel::dismissDialog,
        )
        null -> Unit
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = WdipiShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
    Row(
        modifier = (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(horizontal = WdipiSpacing.lg, vertical = WdipiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        Spacer(Modifier.width(WdipiSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            subtitle?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.heightIn(min = WdipiSpacing.minTouchTarget),
            ) {
                Text(stringResource(label))
            }
        }
    }
}
