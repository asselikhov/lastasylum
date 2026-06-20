package com.lastasylum.alliance.ui.components.team.journal

enum class TeamNewsGalleryLayout {
    None,
    SingleHero,
    TwoColumn,
    ThreeMasonry,
    FourGrid,
    FourPlusOverlay,
}

fun teamNewsGalleryLayout(imageCount: Int): TeamNewsGalleryLayout = when {
    imageCount <= 0 -> TeamNewsGalleryLayout.None
    imageCount == 1 -> TeamNewsGalleryLayout.SingleHero
    imageCount == 2 -> TeamNewsGalleryLayout.TwoColumn
    imageCount == 3 -> TeamNewsGalleryLayout.ThreeMasonry
    imageCount == 4 -> TeamNewsGalleryLayout.FourGrid
    else -> TeamNewsGalleryLayout.FourPlusOverlay
}
