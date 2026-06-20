package com.lastasylum.alliance.ui.screens.teamnews

import com.lastasylum.alliance.ui.components.team.journal.JournalEditorMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamNewsEditorScrollTest {
    @Test
    fun createNews_titleFieldIndex() {
        assertEquals(
            1,
            teamNewsEditorScrollIndex(
                field = TeamNewsEditorScrollField.Title,
                hasErrorBanner = false,
                isCreate = true,
                editorMode = JournalEditorMode.News,
                showPollSection = false,
            ),
        )
    }

    @Test
    fun pollOnly_questionFieldIndex() {
        assertEquals(
            1,
            teamNewsEditorScrollIndex(
                field = TeamNewsEditorScrollField.PollQuestion,
                hasErrorBanner = false,
                isCreate = true,
                editorMode = JournalEditorMode.PollOnly,
                showPollSection = true,
            ),
        )
    }
}
