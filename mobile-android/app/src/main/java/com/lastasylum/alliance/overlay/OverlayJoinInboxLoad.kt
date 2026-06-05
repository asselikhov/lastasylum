package com.lastasylum.alliance.overlay

import android.content.Context
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamJoinRequestDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.ui.OVERLAY_PANEL_LOAD_MAX_MS
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException

internal suspend fun TeamsRepository.loadPendingJoinRequestsForOverlay(
    context: Context,
): Result<List<TeamJoinRequestDto>> {
    return withTimeoutOrNull(OVERLAY_PANEL_LOAD_MAX_MS) {
        listPendingJoinRequests()
    } ?: Result.failure(
        SocketTimeoutException(context.getString(R.string.team_inbox_load_failed)),
    )
}

internal fun joinInboxLoadErrorMessage(context: Context, error: Throwable): String =
    error.toUserMessageRu(context.resources).ifBlank {
        context.getString(R.string.team_inbox_load_failed)
    }
