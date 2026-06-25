package com.lastasylum.alliance.game

import androidx.annotation.StringRes
import com.lastasylum.alliance.R

/**
 * Единый источник стиля рейд-команд («Атака»/«Штурм»/«Подкрепление»): полная подпись для
 * сообщения чата и цвет. Используется и в оверлей-карточке «В рейд»
 * ([com.lastasylum.alliance.overlay.OverlayRaidSharePanel]), и в рендерере чата
 * ([com.lastasylum.alliance.ui.chat.MapLinkedMessageText]), чтобы цвета и подписи не расходились.
 *
 * Цвет хранится в ARGB ([Long]); в оверлее конвертируется в `android.graphics.Color`,
 * в чате — в `androidx.compose.ui.graphics.Color`.
 */
enum class RaidCommandStyle(
    @StringRes val fullLabelRes: Int,
    val colorArgb: Long,
) {
    ATTACK(R.string.overlay_cmd_column_attack, 0xFFF43F5E),
    STORM(R.string.overlay_cmd_column_storm, 0xFFF59E0B),
    REINFORCE(R.string.overlay_cmd_column_reinf, 0xFF22C55E),
    ;

    companion object {
        val ALL: List<RaidCommandStyle> = entries
    }
}
