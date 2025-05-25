package com.dogancaglar.paymentservice.adapter.delayqueue.mapper

import com.dogancaglar.paymentservice.adapter.delayqueue.ScheduledPaymentOrderStatusRequestEntity
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class ScheduledPaymentOrderRequestMapper() {

    fun toEntity(
        envelopeJson: String,
        delaySecond: Long,
        paymentOrderId: String
    ): ScheduledPaymentOrderStatusRequestEntity {
        // Deserialize the envelope to extract the eventId
        return ScheduledPaymentOrderStatusRequestEntity(
            payload = envelopeJson,
            sendAfter = Instant.now(Clock.system(ZoneId.of("Europe/Amsterdam"))).plusSeconds(delaySecond),
            createdAt = LocalDateTime.now(ZoneId.of("Europe/Amsterdam")),
            paymentOrderId = paymentOrderId
        )
    }
}