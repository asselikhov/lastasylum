package com.lastasylum.alliance.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.components.PrimaryPanel
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AuthMode {
    Login,
    Register,
    Forgot,
    Reset,
}

@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onLoginClick: (email: String, password: String) -> Unit,
    onRegisterClick: (username: String, email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onResetPassword: (email: String, token: String, newPassword: String) -> Unit,
    onClearError: () -> Unit,
) {
    var mode by remember { mutableStateOf(AuthMode.Login) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var resetToken by remember { mutableStateOf("") }
    var newPasswordReset by remember { mutableStateOf("") }
    var newPasswordResetConfirm by remember { mutableStateOf("") }

    val scroll = rememberScrollState()
    val buildTimeStr = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(BuildConfig.BUILD_TIME_MS))
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(
                    horizontal = SquadRelayDimens.screenPaddingHorizontal,
                    vertical = SquadRelayDimens.screenPaddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.sectionGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.headerSubtitleGap)) {
                Text(
                    text = stringResource(R.string.auth_brand),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.auth_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryPanel {
                Column(
                    verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.blockGap),
                ) {
                    when (mode) {
                        AuthMode.Login, AuthMode.Register -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = mode == AuthMode.Login,
                                    onClick = {
                                        mode = AuthMode.Login
                                        onClearError()
                                    },
                                    label = { Text(stringResource(R.string.auth_tab_login)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                FilterChip(
                                    selected = mode == AuthMode.Register,
                                    onClick = {
                                        mode = AuthMode.Register
                                        onClearError()
                                    },
                                    label = { Text(stringResource(R.string.auth_tab_register)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                        else -> {
                            TextButton(
                                onClick = {
                                    mode = AuthMode.Login
                                    onClearError()
                                },
                            ) {
                                Text(stringResource(R.string.auth_back_to_login))
                            }
                        }
                    }

                    when (mode) {
                        AuthMode.Forgot -> {
                            Text(
                                text = stringResource(R.string.auth_forgot_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim() },
                                label = { Text(stringResource(R.string.auth_email)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )
                            val canForgot = !isLoading && email.isNotBlank()
                            Button(
                                onClick = { onForgotPassword(email) },
                                enabled = canForgot,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text(stringResource(R.string.auth_forgot_send))
                                }
                            }
                            TextButton(onClick = { mode = AuthMode.Reset; onClearError() }) {
                                Text(stringResource(R.string.auth_reset_title))
                            }
                        }
                        AuthMode.Reset -> {
                            Text(
                                text = stringResource(R.string.auth_reset_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim() },
                                label = { Text(stringResource(R.string.auth_email)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )
                            OutlinedTextField(
                                value = resetToken,
                                onValueChange = { resetToken = it.trim() },
                                label = { Text(stringResource(R.string.auth_reset_token)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )
                            OutlinedTextField(
                                value = newPasswordReset,
                                onValueChange = { newPasswordReset = it },
                                label = { Text(stringResource(R.string.auth_reset_new_password)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )
                            OutlinedTextField(
                                value = newPasswordResetConfirm,
                                onValueChange = { newPasswordResetConfirm = it },
                                label = { Text(stringResource(R.string.auth_password_confirm)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )
                            val resetMatch =
                                newPasswordReset == newPasswordResetConfirm && newPasswordReset.length >= 8
                            val canReset = !isLoading &&
                                email.isNotBlank() &&
                                resetToken.length == 64 &&
                                resetMatch
                            Button(
                                onClick = {
                                    onResetPassword(email, resetToken, newPasswordReset)
                                },
                                enabled = canReset,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text(stringResource(R.string.auth_reset_submit))
                                }
                            }
                            if (newPasswordReset.isNotEmpty() && newPasswordResetConfirm.isNotEmpty() && !resetMatch) {
                                Text(
                                    text = stringResource(R.string.auth_password_mismatch),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        AuthMode.Login, AuthMode.Register -> {
                            if (mode == AuthMode.Register) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it.trimStart() },
                                    label = { Text(stringResource(R.string.auth_username)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim() },
                                label = { Text(stringResource(R.string.auth_email)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(stringResource(R.string.auth_password)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )

                            if (mode == AuthMode.Register) {
                                OutlinedTextField(
                                    value = passwordConfirm,
                                    onValueChange = { passwordConfirm = it },
                                    label = { Text(stringResource(R.string.auth_password_confirm)) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }

                            if (mode == AuthMode.Login) {
                                TextButton(
                                    onClick = {
                                        mode = AuthMode.Forgot
                                        onClearError()
                                    },
                                    modifier = Modifier.align(Alignment.Start),
                                ) {
                                    Text(stringResource(R.string.auth_forgot_link))
                                }
                            }

                            val canSubmitLogin = !isLoading && email.isNotBlank() && password.length >= 8
                            val passwordsMatch = password == passwordConfirm && password.isNotEmpty()
                            val canSubmitRegister = !isLoading &&
                                username.length >= 3 &&
                                email.isNotBlank() &&
                                password.length >= 8 &&
                                passwordsMatch

                            Button(
                                onClick = {
                                    when (mode) {
                                        AuthMode.Login -> onLoginClick(email, password)
                                        AuthMode.Register -> onRegisterClick(username, email, password)
                                        else -> Unit
                                    }
                                },
                                enabled = when (mode) {
                                    AuthMode.Login -> canSubmitLogin
                                    AuthMode.Register -> canSubmitRegister
                                    else -> false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text(
                                        text = when (mode) {
                                            AuthMode.Login -> stringResource(R.string.auth_button_login)
                                            AuthMode.Register -> stringResource(R.string.auth_button_register)
                                            else -> ""
                                        },
                                    )
                                }
                            }

                            if (mode == AuthMode.Register && password.isNotEmpty() && passwordConfirm.isNotEmpty() && !passwordsMatch) {
                                Text(
                                    text = stringResource(R.string.auth_password_mismatch),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (!infoMessage.isNullOrBlank()) {
                        Text(
                            text = infoMessage,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.auth_build_footer,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    buildTimeStr,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
