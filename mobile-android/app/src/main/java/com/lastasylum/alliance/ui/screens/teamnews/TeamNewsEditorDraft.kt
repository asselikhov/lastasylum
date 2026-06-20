package com.lastasylum.alliance.ui.screens.teamnews

import com.lastasylum.alliance.ui.components.team.journal.JournalEditorMode

data class EditorAttachment(
    val fileId: String?,
    val previewPath: String?,
    val uploading: Boolean = false,
    val uploadFailed: Boolean = false,
)

data class TeamNewsEditorDraft(
    val mode: JournalEditorMode,
    val title: String,
    val body: String,
    val attachments: List<EditorAttachment>,
    val includePoll: Boolean,
    val pollQuestion: String,
    val pollOptions: List<String>,
    val pollOptionIds: List<String?>,
    val isEdit: Boolean,
    val newsId: String?,
)

/** In-memory draft between editor and preview (same nav session). */
object TeamNewsEditorDraftHolder {
    var draft: TeamNewsEditorDraft? = null
}
