package com.lastasylum.alliance.ui.chat

/** Быстрые реакции в шитe сообщения — только Unicode-escape, без UTF-8 в исходнике (Windows/CI). */
object ChatQuickReactions {
    val defaults: List<String> = listOf(
        "\uD83D\uDC4D", // 👍
        "\u2764\uFE0F", // ❤️
        "\uD83D\uDE02", // 😂
        "\uD83D\uDD25", // 🔥
        "\uD83C\uDF89", // 🎉
        "\uD83D\uDC4F", // 👏
    )
}
