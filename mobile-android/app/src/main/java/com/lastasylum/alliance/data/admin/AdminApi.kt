package com.lastasylum.alliance.data.admin

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface AdminApi {
    @GET("admin/alliances")
    suspend fun listAlliances(): List<AllianceAdminDto>

    @PATCH("admin/alliances/{publicId}/overlay")
    suspend fun updateAllianceOverlay(
        @Path("publicId") publicId: String,
        @Body body: UpdateAllianceOverlayBody,
    ): AllianceAdminDto
}
