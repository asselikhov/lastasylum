package com.lastasylum.alliance.overlay

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionBurstReplyTo
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayReactionCaptionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun formatReactionDisplayNick_stripsAtPrefix() {
        assertEquals("Player", OverlayReactionNickFormat.format("@Player"))
        assertEquals("Player", OverlayReactionNickFormat.format("Player"))
    }

    @Test
    fun formatReactionDisplayNick_blankFallback() {
        assertEquals("—", OverlayReactionNickFormat.format("  "))
    }

    @Test
    fun heroCaptionBlock_broadcastScopeThenFromLine() {
        val block = OverlayReactionCaption.createHeroCaptionBlock(
            context = context,
            displayName = "RustlingGrass",
            broadcast = true,
            isReply = false,
        )
        assertEquals(
            "[${context.getString(R.string.overlay_reaction_burst_caption_broadcast)}]",
            block.scopeLabelView.text.toString(),
        )
        assertEquals(
            context.getString(R.string.overlay_reaction_burst_from, "RustlingGrass"),
            block.fromLineView.text.toString(),
        )
        assertEquals(1, block.root.childCount)
        assertEquals(2, block.textColumn.childCount)
    }

    @Test
    fun heroCaptionBlock_replyParentPreviewBesideTwoLines() {
        val block = OverlayReactionCaption.createHeroCaptionBlock(
            context = context,
            displayName = "Alice",
            broadcast = false,
            isReply = true,
        )
        assertTrue(
            block.scopeLabelView.text.toString().contains(
                context.getString(R.string.overlay_notifications_reply_scope),
            ),
        )
        val visualFactory = OverlayReactionVisualFactory(context) { }
        OverlayReactionBurstReplyPreview.attachBesideCaptionLines(
            caption = block,
            context = context,
            replyTo = OverlayReactionBurstReplyTo(
                logId = "log1",
                reaction = "heart",
                visibility = OverlayReactionLogVisibility.Personal,
            ),
            visualFactory = visualFactory,
            dp = { it },
            hero = true,
        )
        assertEquals(LinearLayout.HORIZONTAL, block.root.orientation)
        assertEquals(2, block.root.childCount)
        assertEquals(1, block.scopeRow.childCount)
        assertTrue(block.root.getChildAt(1) is android.widget.FrameLayout)
    }

    @Test
    fun updateMergeCount_updatesScopeLineOnly() {
        val block = OverlayReactionCaption.createHeroCaptionBlock(
            context = context,
            displayName = "Bob",
            broadcast = true,
            mergeCount = 1,
        )
        OverlayReactionCaption.updateMergeCount(
            captionRoot = block.root,
            displayName = "Bob",
            broadcast = true,
            isReply = false,
            mergeCount = 3,
        )
        val mergeSuffix = context.getString(R.string.overlay_reaction_burst_merge_count, 3)
        assertTrue(block.scopeLabelView.text.toString().contains(mergeSuffix))
        assertEquals(
            context.getString(R.string.overlay_reaction_burst_from, "Bob"),
            block.fromLineView.text.toString(),
        )
    }
}
