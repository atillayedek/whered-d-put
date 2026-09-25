package com.wheredidiputit.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.component.WdipiTopBar
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing

private val sections = listOf(
    R.string.privacy_what_title to R.string.privacy_what_body,
    R.string.privacy_where_title to R.string.privacy_where_body,
    R.string.privacy_who_title to R.string.privacy_who_body,
    R.string.privacy_photos_title to R.string.privacy_photos_body,
    R.string.privacy_mic_title to R.string.privacy_mic_body,
    R.string.privacy_ads_title to R.string.privacy_ads_body,
    R.string.privacy_delete_title to R.string.privacy_delete_body,
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { WdipiTopBar(title = stringResource(R.string.settings_privacy), onNavigate = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WdipiSpacing.screen, vertical = WdipiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WdipiSpacing.xl),
        ) {
            Text(
                stringResource(R.string.privacy_intro),
                style = MaterialTheme.typography.bodyLarge,
            )
            sections.forEach { (title, body) -> PrivacySection(title, body) }
        }
    }
}

@Composable
private fun PrivacySection(@StringRes title: Int, @StringRes body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(WdipiSpacing.xs)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
