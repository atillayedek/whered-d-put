package com.wheredidiputit.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.component.EmptyState
import com.wheredidiputit.core.designsystem.component.PrimaryButton
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing
import com.wheredidiputit.presentation.common.LanguageSelector

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = WdipiSpacing.xl, vertical = WdipiSpacing.xl),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WdipiSpacing.xl),
        ) {
            LanguageSelector()
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(WdipiSpacing.sm)) {
                Text(
                    stringResource(R.string.app_name_display),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.onboarding_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(WdipiSpacing.lg)) {
                Feature(Icons.Outlined.EditNote, R.string.onboarding_write_title, R.string.onboarding_write_body)
                Feature(Icons.Outlined.Mic, R.string.onboarding_speak_title, R.string.onboarding_speak_body)
                Feature(Icons.Outlined.PhotoCamera, R.string.onboarding_photo_title, R.string.onboarding_photo_body)
            }
        }
        PrimaryButton(
            text = stringResource(R.string.onboarding_get_started),
            onClick = onGetStarted,
            modifier = Modifier.padding(top = WdipiSpacing.lg),
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, titleRes: Int, bodyRes: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(WdipiSpacing.lg))
        Column {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shown only when the build has no backend credentials. It states plainly
 * that accounts and sync are unavailable instead of pretending to work.
 */
@Composable
fun SetupRequiredScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.setup_required_title),
            message = stringResource(R.string.setup_required_message),
        )
    }
}
