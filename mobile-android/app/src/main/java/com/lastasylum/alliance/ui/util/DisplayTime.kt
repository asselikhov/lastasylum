package com.lastasylum.alliance.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/** Единая зона отображения дат/времени в UI (МСК). Сервер отдаёт UTC в ISO-8601. */
val APP_DISPLAY_ZONE: ZoneId = ZoneId.of("Europe/Moscow")

private val LOCALE_RU: Locale = Locale.forLanguageTag("ru")

private val ISO_LOCAL_DATE_TIME_FLEX: DateTimeFormatter = DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .appendLiteral('T')
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .optionalStart()
    .appendLiteral(':')
    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
    .optionalEnd()
    .optionalEnd()
    .toFormatter()

/**
 * Разбор ISO с зоной (`Z` / offset) или без зоны.
 * Без зоны — UTC (Mongo/Nest), не локальное МСК: иначе время в чатах отстаёт на 3 ч.
 */
fun parseIsoInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    val s = iso.trim()
    runCatching { Instant.parse(s) }.getOrNull()?.let { return it }
    runCatching {
        java.time.OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    }.getOrNull()?.let { return it }
    runCatching {
        LocalDateTime.parse(s, ISO_LOCAL_DATE_TIME_FLEX)
            .atZone(ZoneOffset.UTC)
            .toInstant()
    }.getOrNull()?.let { return it }
    runCatching {
        LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneOffset.UTC)
            .toInstant()
    }.getOrNull()?.let { return it }
    return null
}

/** Epoch millis for sorting/clustering; null if unparseable. */
fun parseIsoInstantEpochMilli(iso: String?): Long? = parseIsoInstant(iso)?.toEpochMilli()

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
