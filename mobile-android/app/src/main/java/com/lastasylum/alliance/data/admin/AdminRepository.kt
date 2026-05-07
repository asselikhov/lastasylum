package com.lastasylum.alliance.data.admin

class AdminRepository(
    private val adminApi: AdminApi,
) {
    suspend fun listAlliances(): Result<List<AllianceAdminDto>> =
        runCatching { adminApi.listAlliances() }

    suspend fun setOverlayEnabled(
        publicId: String,
        enabled: Boolean,
    ): Result<AllianceAdminDto> =
        runCatching {
            adminApi.updateAllianceOverlay(
                publicId,
                UpdateAllianceOverlayBody(overlayEnabled = enabled),
            )
        }

    suspend fun getStickerAccess(allianceCode: String): Result<AllianceStickerAccessDto> =
        runCatching { adminApi.getStickerAccess(allianceCode) }

    suspend fun putStickerAccess(
        allianceCode: String,
        body: PutAllianceStickerAccessBody,
    ): Result<AllianceStickerAccessDto> =
        runCatching { adminApi.putStickerAccess(allianceCode, body) }
}
