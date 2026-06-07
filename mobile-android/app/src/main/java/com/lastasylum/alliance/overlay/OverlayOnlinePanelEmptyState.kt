package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.R

data class OverlayOnlineFilterCounts(
    val all: Int,
    val ingame: Int,
    val withMic: Int,
    val recent: Int,
)

fun computeOverlayOnlineFilterCounts(
    baseSections: List<OverlayOnlinePresenceSection>,
    voiceFlagsByUserId: Map<String, VoiceMemberFlags>,
): OverlayOnlineFilterCounts {
    val ingame = baseSections.firstOrNull { it.kind == PresenceSectionKind.Ingame }?.items.orEmpty()
    val recent = baseSections.firstOrNull { it.kind == PresenceSectionKind.Recent }?.items.orEmpty()
    val withMic = (ingame + recent).count { voiceFlagsByUserId[it.userId]?.micOn == true }
    return OverlayOnlineFilterCounts(
        all = ingame.size + recent.size,
        ingame = ingame.size,
        withMic = withMic,
        recent = recent.size,
    )
}

/** String resource for empty list, or null when items are visible. */
fun resolveOverlayOnlinePanelEmptyMessageRes(
    activeFilterChip: OverlayOnlineFilterChip,
    searchQuery: String,
    filterCounts: OverlayOnlineFilterCounts,
    totalVisible: Int,
): Int? {
    if (totalVisible > 0) return null
    if (searchQuery.trim().isNotEmpty()) return R.string.overlay_online_empty_search
    return when (activeFilterChip) {
        OverlayOnlineFilterChip.WithMic -> R.string.overlay_online_empty_mic
        OverlayOnlineFilterChip.RecentOnly -> R.string.overlay_online_empty_recent
        OverlayOnlineFilterChip.IngameOnly,
        OverlayOnlineFilterChip.All,
        -> R.string.overlay_online_empty
    }
}

fun filterChipLabelRes(chip: OverlayOnlineFilterChip): Int = when (chip) {
    OverlayOnlineFilterChip.All -> R.string.overlay_online_filter_all_count
    OverlayOnlineFilterChip.IngameOnly -> R.string.overlay_online_filter_ingame_count
    OverlayOnlineFilterChip.WithMic -> R.string.overlay_online_filter_with_mic_count
    OverlayOnlineFilterChip.RecentOnly -> R.string.overlay_online_filter_recent_count
}

fun filterChipCountFor(chip: OverlayOnlineFilterChip, counts: OverlayOnlineFilterCounts): Int =
    when (chip) {
        OverlayOnlineFilterChip.All -> counts.all
        OverlayOnlineFilterChip.IngameOnly -> counts.ingame
        OverlayOnlineFilterChip.WithMic -> counts.withMic
        OverlayOnlineFilterChip.RecentOnly -> counts.recent
    }
