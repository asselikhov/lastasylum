package com.lastasylum.alliance.data.admin

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AllianceAdminDto(
    val allianceCode: String,
    val publicId: String,
    val overlayEnabled: Boolean,
    val memberCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class UpdateAllianceOverlayBody(
    val overlayEnabled: Boolean,
)
