package com.lastasylum.alliance.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.auth.AccountRoles

@Composable
fun accountRoleLabel(role: String): String =
    when (AccountRoles.normalize(role)) {
        AccountRoles.MEMBER -> stringResource(R.string.account_role_member)
        AccountRoles.OFFICER -> stringResource(R.string.account_role_officer)
        AccountRoles.MODERATOR -> stringResource(R.string.account_role_moderator)
        AccountRoles.ADMIN -> stringResource(R.string.account_role_admin)
        else -> role
    }
