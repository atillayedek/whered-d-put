package com.wheredidiputit.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.component.EmailField
import com.wheredidiputit.core.designsystem.component.NavIcon
import com.wheredidiputit.core.designsystem.component.PasswordField
import com.wheredidiputit.core.designsystem.component.PrimaryButton
import com.wheredidiputit.core.designsystem.component.QuietButton
import com.wheredidiputit.core.designsystem.component.WdipiTopBar
import com.wheredidiputit.domain.model.AppError

@Composable
fun SignInScreen(
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    onVerifyEmail: (String) -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val focusManager = LocalFocusManager.current
    AuthScaffold(
        title = stringResource(R.string.sign_in_title),
        subtitle = stringResource(R.string.sign_in_subtitle),
    ) {
        EmailField(
            value = viewModel.email,
            onValueChange = viewModel::onEmailChange,
            error = if (viewModel.emailInvalid) stringResource(R.string.auth_email_invalid) else null,
        )
        PasswordField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_password),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                viewModel.signIn()
            }),
        )
        AuthError(viewModel.error)
        if (viewModel.error == AppError.EMAIL_NOT_CONFIRMED) {
            QuietButton(
                text = stringResource(R.string.sign_in_resend_confirmation),
                onClick = { onVerifyEmail(viewModel.email.trim()) },
            )
        }
        PrimaryButton(
            text = stringResource(R.string.sign_in_action),
            onClick = {
                focusManager.clearFocus()
                viewModel.signIn()
            },
            loading = viewModel.isLoading,
        )
        QuietButton(
            text = stringResource(R.string.sign_in_forgot),
            onClick = onForgotPassword,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.sign_in_new_here),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QuietButton(text = stringResource(R.string.sign_in_create_account), onClick = onCreateAccount)
        }
    }
}

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onVerificationRequired: (String) -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SignUpEvent.VerificationRequired -> onVerificationRequired(event.email)
            }
        }
    }
    AuthScaffold(
        title = stringResource(R.string.sign_up_title),
        subtitle = stringResource(R.string.sign_up_subtitle),
        topBar = { WdipiTopBar(title = "", onNavigate = onBack) },
    ) {
        EmailField(
            value = viewModel.email,
            onValueChange = viewModel::onEmailChange,
            error = if (viewModel.emailInvalid) stringResource(R.string.auth_email_invalid) else null,
        )
        PasswordField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_new_password),
            error = if (viewModel.passwordTooShort) {
                stringResource(R.string.auth_password_too_short, MIN_PASSWORD_LENGTH)
            } else {
                null
            },
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                viewModel.signUp()
            }),
        )
        if (!viewModel.passwordTooShort) {
            Text(
                stringResource(R.string.auth_password_rule, MIN_PASSWORD_LENGTH),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AuthError(viewModel.error)
        PrimaryButton(
            text = stringResource(R.string.sign_up_action),
            onClick = {
                focusManager.clearFocus()
                viewModel.signUp()
            },
            loading = viewModel.isLoading,
        )
        QuietButton(
            text = stringResource(R.string.sign_up_have_account),
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
fun VerifyEmailScreen(
    onBackToSignIn: (email: String) -> Unit,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    AuthScaffold(
        title = stringResource(R.string.verify_title),
        subtitle = stringResource(R.string.verify_subtitle, viewModel.email),
        icon = Icons.Outlined.MarkEmailUnread,
    ) {
        Text(
            stringResource(R.string.verify_other_device),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (viewModel.resent) {
            Text(stringResource(R.string.verify_resent), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        AuthError(viewModel.error)
        PrimaryButton(
            text = stringResource(R.string.verify_continue),
            onClick = { onBackToSignIn(viewModel.email) },
        )
        QuietButton(
            text = if (viewModel.cooldownSeconds > 0) {
                stringResource(R.string.verify_resend_in, viewModel.cooldownSeconds)
            } else {
                stringResource(R.string.verify_resend)
            },
            onClick = viewModel::resend,
            enabled = viewModel.cooldownSeconds == 0 && !viewModel.isSending,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val focusManager = LocalFocusManager.current
    AuthScaffold(
        title = stringResource(if (viewModel.sent) R.string.verify_title else R.string.forgot_title),
        subtitle = if (viewModel.sent) {
            stringResource(R.string.forgot_sent, viewModel.email.trim())
        } else {
            stringResource(R.string.forgot_subtitle)
        },
        icon = if (viewModel.sent) Icons.Outlined.MarkEmailUnread else Icons.Outlined.Key,
        topBar = { WdipiTopBar(title = "", onNavigate = onBack) },
    ) {
        if (viewModel.sent) {
            PrimaryButton(text = stringResource(R.string.forgot_back_to_sign_in), onClick = onBack)
        } else {
            EmailField(
                value = viewModel.email,
                onValueChange = viewModel::onEmailChange,
                error = if (viewModel.emailInvalid) stringResource(R.string.auth_email_invalid) else null,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    viewModel.send()
                }),
            )
            AuthError(viewModel.error)
            PrimaryButton(
                text = stringResource(R.string.forgot_action),
                onClick = {
                    focusManager.clearFocus()
                    viewModel.send()
                },
                loading = viewModel.isLoading,
            )
        }
    }
}

@Composable
fun ResetPasswordScreen(
    onFinished: (updated: Boolean) -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(viewModel) {
        viewModel.done.collect { onFinished(true) }
    }
    AuthScaffold(
        title = stringResource(R.string.reset_title),
        subtitle = stringResource(R.string.reset_subtitle),
        icon = Icons.Outlined.LockReset,
        topBar = { WdipiTopBar(title = "", onNavigate = { onFinished(false) }, navIcon = NavIcon.CLOSE) },
    ) {
        PasswordField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_new_password),
            error = if (viewModel.passwordTooShort) {
                stringResource(R.string.auth_password_too_short, MIN_PASSWORD_LENGTH)
            } else {
                null
            },
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                viewModel.save()
            }),
        )
        AuthError(viewModel.error)
        PrimaryButton(
            text = stringResource(R.string.reset_action),
            onClick = {
                focusManager.clearFocus()
                viewModel.save()
            },
            loading = viewModel.isLoading,
        )
    }
}
