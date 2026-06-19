package com.lastasylum.alliance.data.teams

/** Team news inbox activity (new post published). */
data class TeamNewsActivityEvent(
    val teamId: String,
    val newsId: String,
    val createdAt: String,
    val authorUserId: String,
)
