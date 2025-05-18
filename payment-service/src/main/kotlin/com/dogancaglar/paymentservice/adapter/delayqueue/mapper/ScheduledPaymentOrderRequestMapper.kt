package com.dogancaglar.paymentservice.adapter.delayqueue.mapper

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.delayqueue.ScheduledPaymentOrderStatusRequestEntity
import com.dogancaglar.paymentservice.domain.event.ScheduledPaymentOrderStatusRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class ScheduledPaymentOrderRequestMapper() {

    fun  toEntity(
        envelopeJson: String,
        delaySecond: Long
    ): ScheduledPaymentOrderStatusRequestEntity {
        // Deserialize the envelope to extract the eventId
        return ScheduledPaymentOrderStatusRequestEntity(
            payload = envelopeJson,
            sendAfter = Instant.now(Clock.system(ZoneId.of("Europe/Amsterdam"))).plusSeconds(delaySecond ),
            createdAt = LocalDateTime.now(ZoneId.of("Europe/Amsterdam"))
        )
    }
}