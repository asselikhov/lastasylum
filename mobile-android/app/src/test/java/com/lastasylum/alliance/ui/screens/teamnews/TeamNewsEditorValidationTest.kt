package com.lastasylum.alliance.ui.screens.teamnews

import com.lastasylum.alliance.ui.components.team.journal.JournalEditorMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamNewsEditorValidationTest {
    @Test
    fun newsMode_requiresTitleAndBody() {
        val result = validateTeamNewsEditorDraft(
            mode = JournalEditorMode.News,
            title = "",
            body = "text",
            includePoll = false,
            pollQuestion = "",
            pollOptions = listOf("", ""),
            attachments = emptyList(),
            fillRequiredMessageRes = 1,
            pollInvalidMessageRes = 2,
        )
        assertFalse(result.isValid)
        assertTrue(TeamNewsEditorFieldError.Title in result.fieldErrors)
    }

    @Test
    fun pollOnly_requiresQuestionAndTwoOptions() {
        val result = validateTeamNewsEditorDraft(
            mode = JournalEditorMode.PollOnly,
            title = "",
            body = "",
            includePoll = true,
            pollQuestion = "Q?",
            pollOptions = listOf("A", ""),
            attachments = emptyList(),
            fillRequiredMessageRes = 1,
            pollInvalidMessageRes = 2,
        )
        assertFalse(result.isValid)
        assertTrue(TeamNewsEditorFieldError.Poll in result.fieldErrors)
    }

    @Test
    fun newsWithPoll_validWhenComplete() {
        val result = validateTeamNewsEditorDraft(
            mode = JournalEditorMode.News,
            title = "Title",
            body = "Body",
            includePoll = true,
            pollQuestion = "Question?",
            pollOptions = listOf("One", "Two"),
            attachments = emptyList(),
            fillRequiredMessageRes = 1,
            pollInvalidMessageRes = 2,
        )
        assertTrue(result.isValid)
    }

    @Test
    fun invalidWhileUploading() {
        val result = validateTeamNewsEditorDraft(
            mode = JournalEditorMode.News,
            title = "Title",
            body = "Body",
            includePoll = false,
            pollQuestion = "",
            pollOptions = listOf("", ""),
            attachments = listOf(EditorAttachment(fileId = null, previewPath = "x", uploading = true)),
            fillRequiredMessageRes = 1,
            pollInvalidMessageRes = 2,
        )
        assertFalse(result.isValid)
    }
}
