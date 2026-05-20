package com.lastasylum.alliance.data.auth

/** App account roles (JWT / user.role). Squad ranks R1–R5 are separate ([PlayerTeamMemberRole]). */
object AccountRoles {
    const val MEMBER = "MEMBER"
    const val OFFICER = "OFFICER"
    const val MODERATOR = "MODERATOR"
    const val ADMIN = "ADMIN"

    val ALL = listOf(MEMBER, OFFICER, MODERATOR, ADMIN)

    fun isAppAdmin(role: String?): Boolean {
        val r = role?.trim()?.uppercase() ?: return false
        return r == ADMIN || r == "R5"
    }

    fun normalize(raw: String?): String {
        val r = raw?.trim()?.uppercase().orEmpty()
        return when (r) {
            MEMBER, OFFICER, MODERATOR, ADMIN -> r
            "R2" -> MEMBER
            "R3" -> OFFICER
            "R4" -> MODERATOR
            "R5" -> ADMIN
            else -> MEMBER
        }
    }
}
