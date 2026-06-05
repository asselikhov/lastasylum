package com.lastasylum.alliance.data.chat

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/** Lenient REST parsing for pin previews (null strings, `_id` or `id`). */
internal class PinnedMessagePreviewDtoJsonAdapter : JsonAdapter<PinnedMessagePreviewDto>() {
    override fun fromJson(reader: JsonReader): PinnedMessagePreviewDto {
        var mongoId: String? = null
        var legacyId: String? = null
        var text = ""
        var senderUsername = ""
        var senderTeamTag: String? = null
        var senderServerNumber: Int? = null
        var createdAt = ""
        var editedAt: String? = null
        var hasImage = false
        var isSticker = false
        var imageThumbnailUrl: String? = null
        var pinnedByUsername: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "_id" -> mongoId = reader.nextStringValue()
                "id" -> legacyId = reader.nextStringValue()
                "text" -> text = reader.nextStringValue().orEmpty()
                "senderUsername" -> senderUsername = reader.nextStringValue().orEmpty()
                "senderTeamTag" -> senderTeamTag = reader.nextStringValue()
                "senderServerNumber" -> senderServerNumber = reader.nextIntValue()
                "createdAt" -> createdAt = reader.nextStringValue().orEmpty()
                "editedAt" -> editedAt = reader.nextStringValue()
                "hasImage" -> hasImage = reader.nextBooleanValue() ?: false
                "isSticker" -> isSticker = reader.nextBooleanValue() ?: false
                "imageThumbnailUrl" -> imageThumbnailUrl = reader.nextStringValue()
                "pinnedByUsername" -> pinnedByUsername = reader.nextStringValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val id = mongoId?.trim().orEmpty().ifEmpty { legacyId?.trim().orEmpty() }
        if (id.isEmpty()) {
            throw JsonDataException("Pinned preview missing id")
        }
        if (createdAt.isBlank()) {
            createdAt = "1970-01-01T00:00:00.000Z"
        }

        return PinnedMessagePreviewDto(
            mongoId = mongoId?.trim()?.takeIf { it.isNotEmpty() },
            legacyId = legacyId?.trim()?.takeIf { it.isNotEmpty() },
            text = text,
            senderUsername = senderUsername,
            senderTeamTag = senderTeamTag?.trim()?.takeIf { it.isNotEmpty() },
            senderServerNumber = senderServerNumber,
            createdAt = createdAt,
            editedAt = editedAt?.trim()?.takeIf { it.isNotEmpty() },
            hasImage = hasImage,
            isSticker = isSticker,
            imageThumbnailUrl = imageThumbnailUrl?.trim()?.takeIf { it.isNotEmpty() },
            pinnedByUsername = pinnedByUsername?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    override fun toJson(writer: JsonWriter, value: PinnedMessagePreviewDto?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        writer.name("id")
        writer.value(value.id)
        writer.name("text")
        writer.value(value.text)
        writer.name("senderUsername")
        writer.value(value.senderUsername)
        writer.name("senderTeamTag")
        writer.nullableValue(value.senderTeamTag)
        writer.name("senderServerNumber")
        writer.nullableValue(value.senderServerNumber)
        writer.name("createdAt")
        writer.value(value.createdAt)
        writer.name("editedAt")
        writer.nullableValue(value.editedAt)
        writer.name("hasImage")
        writer.value(value.hasImage)
        writer.name("isSticker")
        writer.value(value.isSticker)
        writer.name("imageThumbnailUrl")
        writer.nullableValue(value.imageThumbnailUrl)
        writer.name("pinnedByUsername")
        writer.nullableValue(value.pinnedByUsername)
        writer.endObject()
    }

    companion object {
        val FACTORY = JsonAdapter.Factory { type: Type, _: Set<Annotation>, _: Moshi ->
            if (Types.getRawType(type) != PinnedMessagePreviewDto::class.java) return@Factory null
            PinnedMessagePreviewDtoJsonAdapter().nullSafe()
        }
    }
}

private fun JsonReader.nextStringValue(): String? =
    when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any>()
            null
        }
        else -> nextString()
    }

private fun JsonReader.nextIntValue(): Int? =
    when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any>()
            null
        }
        JsonReader.Token.NUMBER -> nextInt()
        else -> {
            skipValue()
            null
        }
    }

private fun JsonReader.nextBooleanValue(): Boolean? =
    when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any>()
            null
        }
        JsonReader.Token.BOOLEAN -> nextBoolean()
        else -> {
            skipValue()
            null
        }
    }

private fun JsonWriter.nullableValue(value: String?) {
    if (value == null) nullValue() else value(value)
}

private fun JsonWriter.nullableValue(value: Int?) {
    if (value == null) nullValue() else value(value)
}
