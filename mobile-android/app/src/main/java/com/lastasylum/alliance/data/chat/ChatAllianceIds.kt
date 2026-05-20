package com.lastasylum.alliance.data.chat

/** Backend chat room scopes. */
object ChatAllianceIds {
    /** Cross-server lobby («Межсерв»). */
    const val GLOBAL = "__global__"

    const val SERVER_PREFIX = "srv:"

    fun isServerScope(allianceId: String?): Boolean =
        !allianceId.isNullOrBlank() && allianceId.startsWith(SERVER_PREFIX)
}
