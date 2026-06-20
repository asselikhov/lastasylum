package com.lastasylum.alliance.ui.screens.teamnews

import com.lastasylum.alliance.ui.components.team.journal.JournalEditorMode

enum class TeamNewsEditorFieldError {
    Title,
    Body,
    Poll,
}

data class TeamNewsEditorValidationResult(
    val isValid: Boolean,
    val fieldErrors: Set<TeamNewsEditorFieldError> = emptySet(),
    val messageRes: Int? = null,
)

internal fun validateTeamNewsEditorDraft(
    mode: JournalEditorMode,
    title: String,
    body: String,
    includePoll: Boolean,
    pollQuestion: String,
    pollOptions: List<String>,
    attachments: List<EditorAttachment>,
    fillRequiredMessageRes: Int,
    pollInvalidMessageRes: Int,
    maxImagesMessageRes: Int? = null,
    maxImages: Int = MAX_TEAM_NEWS_IMAGES,
    isEdit: Boolean = false,
): TeamNewsEditorValidationResult {
    if (attachments.any { it.uploading }) {
        return TeamNewsEditorValidationResult(isValid = false)
    }
    val pollOnly = mode == JournalEditorMode.PollOnly
    val wantsPoll = when {
        pollOnly -> true
        isEdit -> includePoll
        else -> false
    }
    val errors = mutableSetOf<TeamNewsEditorFieldError>()
    var messageRes: Int? = null

    if (!pollOnly && attachments.count { !it.fileId.isNullOrBlank() } > maxImages) {
        return TeamNewsEditorValidationResult(
            isValid = false,
            messageRes = maxImagesMessageRes,
        )
    }
    if (wantsPoll) {
        val filled = pollOptions.count { it.isNotBlank() }
        if (pollQuestion.isBlank() || filled < 2) {
            errors += TeamNewsEditorFieldError.Poll
            messageRes = pollInvalidMessageRes
        }
    }
    if (!pollOnly) {
        if (title.isBlank()) {
            errors += TeamNewsEditorFieldError.Title
            messageRes = fillRequiredMessageRes
        }
        if (body.isBlank()) {
            errors += TeamNewsEditorFieldError.Body
            messageRes = fillRequiredMessageRes
        }
    }
    return TeamNewsEditorValidationResult(
        isValid = errors.isEmpty(),
        fieldErrors = errors,
        messageRes = messageRes,
    )
}

const val MAX_TEAM_NEWS_IMAGES = 10
