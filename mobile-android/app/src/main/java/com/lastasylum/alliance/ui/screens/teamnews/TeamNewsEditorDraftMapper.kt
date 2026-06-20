package com.lastasylum.alliance.ui.screens.teamnews

import com.lastasylum.alliance.data.teams.TeamNewsDetailDto
import com.lastasylum.alliance.data.teams.TeamNewsPollDetailDto
import com.lastasylum.alliance.data.teams.TeamNewsPollOptionDto
import com.lastasylum.alliance.data.teams.TeamNewsPollTallyDto
import com.lastasylum.alliance.ui.components.team.journal.JournalEditorMode
import java.time.Instant

internal fun TeamNewsEditorDraft.toDetailPreview(
    teamId: String,
    authorUserId: String,
    authorUsername: String,
): TeamNewsDetailDto {
    val pollOnly = mode == JournalEditorMode.PollOnly
    val wantsPoll = pollOnly || includePoll
    val imagePaths = attachments.mapNotNull { it.previewPath?.trim()?.takeIf { p -> p.isNotEmpty() } }
    val now = Instant.now().toString()
    val pollDto = if (wantsPoll) {
        val options = pollOptions.mapIndexedNotNull { index, text ->
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@mapIndexedNotNull null
            TeamNewsPollOptionDto(
                id = pollOptionIds.getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }
                    ?: "preview_$index",
                text = trimmed,
            )
        }
        TeamNewsPollDetailDto(
            question = pollQuestion.trim(),
            options = options,
            votes = emptyList(),
            tallies = options.map { TeamNewsPollTallyDto(optionId = it.id, count = 0) },
            myVoteOptionId = null,
        )
    } else {
        null
    }
    val titleText = if (pollOnly) pollQuestion.trim() else title.trim()
    return TeamNewsDetailDto(
        id = newsId ?: "preview",
        teamId = teamId,
        title = titleText,
        excerpt = if (pollOnly) "" else body.trim().take(220),
        authorUserId = authorUserId,
        authorUsername = authorUsername,
        createdAt = now,
        updatedAt = now,
        hasPoll = wantsPoll,
        pollOnly = pollOnly,
        pollQuestion = pollDto?.question,
        pollOptions = pollDto?.options,
        firstImageRelativeUrl = imagePaths.firstOrNull(),
        pollTallies = emptyList(),
        myVoteOptionId = null,
        body = if (pollOnly) "" else body.trim(),
        imageRelativeUrls = imagePaths,
        poll = pollDto,
    )
}

internal fun buildTeamNewsEditorDraft(
    mode: JournalEditorMode,
    title: String,
    body: String,
    attachments: List<EditorAttachment>,
    includePoll: Boolean,
    pollQuestion: String,
    pollOptions: List<String>,
    pollOptionIds: List<String?>,
    newsId: String?,
): TeamNewsEditorDraft = TeamNewsEditorDraft(
    mode = mode,
    title = title,
    body = body,
    attachments = attachments.toList(),
    includePoll = includePoll,
    pollQuestion = pollQuestion,
    pollOptions = pollOptions.toList(),
    pollOptionIds = pollOptionIds.toList(),
    isEdit = newsId != null,
    newsId = newsId,
)
