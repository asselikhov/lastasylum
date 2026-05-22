package com.lastasylum.alliance.overlay

/** Synthetic [ChatMessage._id] values for overlay strip notices. */
internal object OverlayStripNoticeIds {
    const val GENERIC = "notice"
    const val NO_RAID = "notice:no_raid"
    const val HISTORY_FAILED = "notice:history_failed"

    fun isNotice(id: String?): Boolean = id?.startsWith("notice") == true

    fun isClickable(id: String?): Boolean = id == NO_RAID
}
