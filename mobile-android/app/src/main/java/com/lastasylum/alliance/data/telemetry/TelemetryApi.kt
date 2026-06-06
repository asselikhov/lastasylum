package com.lastasylum.alliance.data.telemetry

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

interface TelemetryApi {
    @POST("telemetry/delivery")
    suspend fun postDelivery(@Body body: DeliveryBatchDto): TelemetryAckDto
}

@JsonClass(generateAdapter = true)
data class DeliveryBatchDto(val samples: List<DeliverySampleDto>)

@JsonClass(generateAdapter = true)
data class DeliverySampleDto(
    val spanType: String,
    val correlationId: String,
    val durationMs: Long,
    val outcome: String,
    val deviceId: String? = null,
)

@JsonClass(generateAdapter = true)
data class TelemetryAckDto(val inserted: Int = 0)
