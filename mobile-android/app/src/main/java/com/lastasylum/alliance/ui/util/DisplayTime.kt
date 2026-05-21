package com.lastasylum.alliance.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Единая зона отображения дат/времени в UI (МСК). Сервер отдаёт UTC в ISO-8601. */
val APP_DISPLAY_ZONE: ZoneId = ZoneId.of("Europe/Moscow")

private val LOCALE_RU: Locale = Locale.forLanguageTag("ru")

/** Разбор ISO UTC (`…Z` / offset) или локальной даты-времени без зоны (трактуем как МСК). */
fun parseIsoInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    val s = iso.trim()
    return runCatching { Instant.parse(s) }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(APP_DISPLAY_ZONE)
                .toInstant()
        }.getOrNull()
}

private fun mskFormatter(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, LOCALE_RU).withZone(APP_DISPLAY_ZONE)

/** Короткое время в пузырях чата и оверлее (`HH:mm` по МСК). */
fun formatChatTimeMsk(createdAt: String?): String {
    val instant = parseIsoInstant(createdAt) ?: return ""
    return mskFormatter("HH:mm").format(instant)
}

/** Ключ календарного дня `yyyy-MM-dd` по МСК (разделители дней в чате). */
fun chatDayKeyMsk(createdAt: String?): String? {
    val instant = parseIsoInstant(createdAt) ?: return null
    return instant.atZone(APP_DISPLAY_ZONE).toLocalDate().toString()
}

/** Подпись разделителя дня (например «17 апреля») по МСК. */
fun formatChatDaySeparatorMsk(createdAt: String?): String {
    val key = chatDayKeyMsk(createdAt) ?: return ""
    return runCatching {
        LocalDate.parse(key).format(DateTimeFormatter.ofPattern("d MMMM", LOCALE_RU))
    }.getOrDefault("")
}

fun formatPresenceTimestampRu(iso: String?): String {
    val instant = parseIsoInstant(iso) ?: return ""
    return mskFormatter("d MMM yyyy, HH:mm").format(instant)
}

fun formatTeamFeedDateRu(iso: String): String {
    val instant = parseIsoInstant(iso) ?: return iso.trim()
    return mskFormatter("d MMM yyyy, HH:mm").format(instant)
}

fun formatForumTopicTimeRu(iso: String): String {
    val instant = parseIsoInstant(iso) ?: return iso.trim()
    return mskFormatter("d MMM, HH:mm").format(instant)
}

/** Дата без времени (компактные списки). */
fun formatIsoDateShortRu(iso: String?): String {
    val instant = parseIsoInstant(iso) ?: return ""
    return mskFormatter("d MMM yyyy").format(instant)
}

/** Полная метка дата+время (`yyyy-MM-dd HH:mm` по МСК). */
fun formatIsoDateTimeRu(iso: String?): String {
    val instant = parseIsoInstant(iso) ?: return ""
    return mskFormatter("yyyy-MM-dd HH:mm").format(instant)
}
