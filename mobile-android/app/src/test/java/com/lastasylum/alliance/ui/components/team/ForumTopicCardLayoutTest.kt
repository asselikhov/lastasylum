package com.lastasylum.alliance.ui.components.team

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumTopicCardLayoutTest {

    @Test
    fun textBlockHeights_sumToContentHeight() {
        assertEquals(
            ForumTopicCardTokens.cardContentHeight,
            ForumTopicCardTokens.textBlockHeightSum(),
        )
    }

    @Test
    fun cardFixedHeight_matchesDesignTokenAlias() {
        assertEquals(
            FeedCardDesignTokens.forumCardFixedHeight,
            ForumTopicCardTokens.cardFixedHeight,
        )
    }

    @Test
    fun slotWidths_matchGhostButtonAndBadge() {
        assertEquals(ForumTopicCardTokens.ghostButtonSize, ForumTopicCardTokens.actionsSlotWidth)
    }

    @Test
    fun subtitleEndInset_reservesMenuAndOptionalBadge() {
        assertTrue(
            ForumTopicCardTokens.subtitleEndInset(hasUnreadBadge = false) >=
                ForumTopicCardTokens.actionsSlotWidth,
        )
        assertTrue(
            ForumTopicCardTokens.subtitleEndInset(hasUnreadBadge = true) >
                ForumTopicCardTokens.subtitleEndInset(hasUnreadBadge = false),
        )
    }
}
