package com.lastasylum.alliance.game

import org.json.JSONObject

/**
 * Невидимые маркеры (Private Use Area) перед значениями «Мощь»/«Поверженные» в тексте
 * сообщения рейда. Рендерер ([com.lastasylum.alliance.ui.chat.MapLinkedMessageText]) заменяет
 * их на реальные PNG-иконки из игры; в обычном тексте они невидимы и не ломают парсер координат.
 */
object RaidShareGlyphs {
    const val POWER = '\uE000'
    const val KILLS = '\uE001'
}

/**
 * Цель шаринга, перехваченная игровым патчем (панель «Поделиться в чат»).
 *
 * Канал доставки: Frida-бридж в процессе игры пишет JSON в приватный каталог игры,
 * JS читает его и шлёт broadcast [RaidShareBridge.ACTION_SHARE_TARGET] в SquadRelay
 * (см. `.tmp-tools/frida/map_fly_bridge.js`).
 *
 * `shareType=1` — это «зонтик» для объектов карты; тип кодируется в `nameKey`, поэтому
 * игровой хук резолвит читаемое имя через `Config[...]` и присылает [cat] + [name].
 */
data class RaidShareTarget(
    val seq: Long,
    val open: Boolean,
    val x: Int,
    val y: Int,
    val serverNumber: Int?,
    val shareType: Int,
    /** Уже локализованное имя цели (имя игрока, монстра, ресурса, владельца конвоя…). */
    val name: String?,
    val playerName: String?,
    /** Короткий тег альянса владельца (напр. для сундука), если есть. */
    val union: String?,
    /** Категория из игры: player / truck / SlgMonsterInfo / ResourceInfo / SlgRallyInfo / MechCity / … */
    val cat: String?,
    /** Уровень объекта (монстр/ресурс/ралли/город игрока), если есть. */
    val lv: Int?,
    /** Мощь игрока (город игрока), если есть. */
    val power: Long?,
    /** Количество поверженных (killEnemyCount, город игрока), если есть. */
    val kills: Long?,
    /** Верх игрового окна выбора канала (px от верха экрана), для позиции оверлея. */
    val dialogTopPx: Int?,
    /** Имя спрайта «Мощь» из игры (напр. pic_zhanli), если есть. */
    val powerIcon: String?,
    /** Имя спрайта «Поверженные» из игры (напр. pic_jisha), если есть. */
    val killsIcon: String?,
    val secretTaskId: Int?,
    /** Грейд сундука: 3=SR, 4=SSR, 5=UR. */
    val grade: Int?,
    /** Звёзды сундука (secretLevel), 1..5. */
    val stars: Int?,
    /** Грейд конвоя (shareType=3), та же шкала, что у сундуков. */
    val qualityType: Int?,
) {
    val isChest: Boolean get() = secretTaskId != null || grade != null
    val isTruck: Boolean get() = cat == "truck"

    /** SR / SSR / UR по [grade], либо null. */
    fun gradeLabel(): String? = qualityToLabel(grade)

    /** SR / SSR / UR по [qualityType] (конвой), либо null. */
    fun qualityLabel(): String? = qualityToLabel(qualityType)

    /**
     * Владелец сундука для отображения: `[ТЕГ] Ник`, либо просто `Ник`, если тег альянса
     * недоступен. null, если ника нет.
     */
    fun chestOwnerLabel(): String? {
        val nick = cleanGameText(playerName) ?: return null
        val tag = union?.trim()?.takeIf { it.isNotEmpty() }
        return if (tag != null) "[$tag] $nick" else nick
    }

    /**
     * Название цели для первой строки (рядом с координатами): тег альянса + ник игрока,
     * либо `Сундук` / `Конвой` / имя монстра-ресурса. Без уровня/мощи и без координат.
     */
    fun titleLine(): String = when {
        isChest -> "\u0421\u0443\u043D\u0434\u0443\u043A" // Сундук
        isTruck -> "\u041A\u043E\u043D\u0432\u043E\u0439" // Конвой
        else -> cleanGameText(name) ?: cleanGameText(playerName) ?: "\u0426\u0435\u043B\u044C" // Цель
    }

    /** Доп. сведения для второй строки: уровень, мощь, поверженные, грейд/звёзды сундука и т.п. */
    fun metaParts(): List<String> {
        val meta = mutableListOf<String>()
        when {
            isChest -> {
                chestGradeStars()?.let { meta.add(it) }
                chestOwnerLabel()?.let { meta.add(it) }
            }
            isTruck -> {
                qualityLabel()?.let { meta.add(it) }
                (cleanGameText(name) ?: cleanGameText(playerName))?.let { meta.add(it) }
            }
            else -> {
                // Уровень выносится в префикс levelPrefix() (перед тегом альянса).
            }
        }
        return meta
    }

    /**
     * Префикс уровня для первой строки — «Ур.N» (перед тегом альянса), либо null.
     * Не применяется к сундукам/конвоям и если имя уже содержит «ур».
     */
    fun levelPrefix(): String? {
        if (isChest || isTruck) return null
        val level = lv ?: return null
        if (level <= 0) return null
        if (titleLine().contains("\u0443\u0440", ignoreCase = true)) return null
        return "\u0423\u0440.$level" // Ур.N
    }

    /** Компактная подпись мощи для UI/чата. */
    fun powerLabel(): String? = power?.takeIf { it > 0 }?.let { compact(it) }

    /** Компактная подпись поверженных для UI/чата. */
    fun killsLabel(): String? = kills?.takeIf { it > 0 }?.let { compact(it) }

    /** Мета без мощи/поверженных — они показываются отдельно с иконками. */
    fun metaPartsForOverlay(): List<String> = metaParts()

    /** Первая строка целиком (`Ур.26 [OBZH] Ник 54.9M 63.1K`), либо null если данных нет. */
    fun metaLine(): String? {
        val parts = (listOfNotNull(levelPrefix(), titleLine()) + metaPartsForOverlay()).toMutableList()
        powerLabel()?.let { parts.add(it) }
        killsLabel()?.let { parts.add(it) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /** `UR ★★★` для сундука, либо null (публично — для подсветки в UI). */
    fun chestGradeStars(): String? {
        val g = gradeLabel()
        val s = stars
        val out = buildString {
            g?.let { append(it) }
            s?.let { v ->
                if (isNotEmpty()) append(" ")
                append("\u2605".repeat(v.coerceIn(1, 5)))
            }
        }
        return out.takeIf { it.isNotBlank() }
    }

    companion object {
        /** Компактная запись больших чисел: 54901923 → «54.9M», 63068 → «63.1K». */
        private fun compact(n: Long): String = when {
            n >= 1_000_000 -> trimZero(n / 1_000_000.0) + "M"
            n >= 1_000 -> trimZero(n / 1_000.0) + "K"
            else -> n.toString()
        }

        private fun trimZero(v: Double): String {
            val s = String.format(java.util.Locale.US, "%.1f", v)
            return if (s.endsWith(".0")) s.dropLast(2) else s
        }

        private fun qualityToLabel(q: Int?): String? = when (q) {
            3 -> "SR"
            4 -> "SSR"
            5 -> "UR"
            else -> null
        }

        /**
         * Убирает игровые служебные маркеры (jamo-обёртки имён), ведущий префикс сервера
         * (`#109 ` у межсерверных целей — он дублировал бы сервер из координат) и обрезает пробелы.
         */
        private fun cleanGameText(raw: String?): String? =
            raw?.replace("\u3151", "")
                ?.replace("\u3155", "")
                ?.trim()
                ?.replace(Regex("^#\\d+\\s*"), "")
                // Пробел между тегом альянса и ником, если игра прислала их слитно ("[OBZH]Ник").
                ?.replace(Regex("\\](?=\\S)"), "] ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        fun fromJson(json: String?): RaidShareTarget? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                RaidShareTarget(
                    seq = o.optLong("seq", 0L),
                    open = o.optBoolean("open", false),
                    x = o.optInt("x", 0),
                    y = o.optInt("y", 0),
                    serverNumber = o.optInt("sid", 0).takeIf { it > 0 },
                    shareType = o.optInt("shareType", 0),
                    name = o.optString("name", "").trim().takeIf { it.isNotEmpty() },
                    playerName = o.optString("playerName", "").trim().takeIf { it.isNotEmpty() },
                    union = o.optString("union", "").trim().takeIf { it.isNotEmpty() },
                    cat = o.optString("cat", "").trim().takeIf { it.isNotEmpty() },
                    lv = o.optInt("lv", 0).takeIf { it > 0 },
                    power = o.optLong("power", 0L).takeIf { it > 0 },
                    kills = o.optLong("kills", 0L).takeIf { it > 0 },
                    dialogTopPx = o.optInt("dialogTopPx", 0).takeIf { it > 0 },
                    powerIcon = o.optString("powerIcon", "").trim().takeIf { it.isNotEmpty() },
                    killsIcon = o.optString("killsIcon", "").trim().takeIf { it.isNotEmpty() },
                    secretTaskId = o.optInt("secretTaskId", 0).takeIf { it > 0 },
                    grade = o.optInt("grade", 0).takeIf { it > 0 },
                    stars = o.optInt("stars", 0).takeIf { it > 0 },
                    qualityType = o.optInt("qualityType", 0).takeIf { it > 0 },
                )
            }.getOrNull()
        }
    }
}
