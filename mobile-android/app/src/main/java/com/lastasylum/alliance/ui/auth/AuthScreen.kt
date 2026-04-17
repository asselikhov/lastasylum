package com.lastasylum.alliance.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.lastasylum.alliance.R

private enum class AuthMode {
    Login,
    Register,
}

@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onLoginClick: (email: String, password: String) -> Unit,
    onRegisterClick: (username: String, email: String, password: String) -> Unit,
    onClearError: () -> Unit,
) {
    var mode by remember { mutableStateOf(AuthMode.Login) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = stringResource(R.string.auth_brand), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = mode == AuthMode.Login,
                onClick = {
                    mode = AuthMode.Login
                    onClearError()
                },
                label = { Text(stringResource(R.string.auth_tab_login)) },
            )
            FilterChip(
                selected = mode == AuthMode.Register,
                onClick = {
                    mode = AuthMode.Register
                    onClearError()
                },
                label = { Text(stringResource(R.string.auth_tab_register)) },
            )
        }

        if (mode == AuthMode.Register) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trimStart() },
                label = { Text(stringResource(R.string.auth_username)) },
                supportingText = { Text(stringResource(R.string.auth_username_helper)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            supportingText = { Text(stringResource(R.string.auth_password_helper)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (mode == AuthMode.Register) {
            OutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                label = { Text(stringResource(R.string.auth_password_confirm)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
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
                }
            },
            enabled = when (mode) {
                AuthMode.Login -> canSubmitLogin
                AuthMode.Register -> canSubmitRegister
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            } else {
                Text(
                    text = when (mode) {
                        AuthMode.Login -> stringResource(R.string.auth_button_login)
                        AuthMode.Register -> stringResource(R.string.auth_button_register)
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

        if (!errorMessage.isNullOrBlank()) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
        if (!infoMessage.isNullOrBlank()) {
            Text(
                text = infoMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
