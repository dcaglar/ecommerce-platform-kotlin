package com.dogancaglar.paymentservice.adapter

import com.dogancaglar.application.PaymentOrderCreated
import com.dogancaglar.common.event.EventEnvelope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OutboxEventPayloadDeserializationTest {

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `should deserialize stored OutboxEvent payload to EventEnvelope of PaymentOrderCreated`() {
        // given
        val json = """
            {
              "eventId":"35a9f7d5-c6f7-4cbb-a6b5-b5e2870d9390",
              "eventType":"payment_order_created",
              "aggregateId":"paymentorder-4878",
              "data":{
                "paymentOrderId":"4878",
                "publicPaymentOrderId":"paymentorder-4878",
                "paymentId":"2126",
                "publicPaymentId":"payment-2126",
                "sellerId":"SELLER-003",
                "amountValue":120.00,
                "currency":"EUR",
                "status":"INITIATED",
                "createdAt":"2025-05-29T05:58:42.851803",
                "updatedAt":"2025-05-29T05:58:42.851805",
                "retryCount":0,
                "publicId":"payment-2126"
              },
              "timestamp":"2025-05-29T05:58:42.851812",
              "traceId":"6916458f-3c1e-4c3e-818e-2a538eeda854",
              "parentEventId":null
            }
        """.trimIndent()

        // when
        val envelope: EventEnvelope<PaymentOrderCreated> = objectMapper.readValue(
            json,
            object : com.fasterxml.jackson.core.type.TypeReference<EventEnvelope<PaymentOrderCreated>>() {}
        )

        // then
        assertThat(envelope.eventId.toString()).isEqualTo("35a9f7d5-c6f7-4cbb-a6b5-b5e2870d9390")
        assertThat(envelope.traceId).isEqualTo("6916458f-3c1e-4c3e-818e-2a538eeda854")
        assertThat(envelope.eventType).isEqualTo("payment_order_created")
        assertThat(envelope.aggregateId).isEqualTo("paymentorder-4878")
        assertThat(envelope.data.paymentId).isEqualTo("2126")
        assertThat(envelope.data.amountValue).isEqualTo(BigDecimal("120.00"))
        assertThat(envelope.data.sellerId).isEqualTo("SELLER-003")
    }
}