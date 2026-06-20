package com.lastasylum.alliance.ui.screens.teamnews

import com.lastasylum.alliance.ui.components.team.journal.JournalEditorMode

internal enum class TeamNewsEditorScrollField {
    Title,
    Body,
    PollQuestion,
    PollOptions,
}

/**
 * Индекс lazy-item для [androidx.compose.foundation.lazy.LazyListState.animateScrollToItem]
 * при фокусе поля (порядок совпадает с [TeamNewsEditorScreen]).
 */
internal fun teamNewsEditorScrollIndex(
    field: TeamNewsEditorScrollField,
    hasErrorBanner: Boolean,
    isCreate: Boolean,
    editorMode: JournalEditorMode,
    showPollSection: Boolean,
): Int {
    var index = 0
    if (hasErrorBanner) index++
    if (isCreate) index++
    when (editorMode) {
        JournalEditorMode.News -> {
            when (field) {
                TeamNewsEditorScrollField.Title -> return index
                TeamNewsEditorScrollField.Body -> return index + 1
                TeamNewsEditorScrollField.PollQuestion,
                TeamNewsEditorScrollField.PollOptions,
                -> index += 3
            }
        }
        JournalEditorMode.PollOnly -> {
            if (field == TeamNewsEditorScrollField.Title ||
                field == TeamNewsEditorScrollField.Body
            ) {
                return -1
            }
        }
    }
    if (!showPollSection) return -1
    return when (field) {
        TeamNewsEditorScrollField.PollQuestion -> index
        TeamNewsEditorScrollField.PollOptions -> index + 1
        TeamNewsEditorScrollField.Title,
        TeamNewsEditorScrollField.Body,
        -> -1
    }
}
