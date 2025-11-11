package com.dogancaglar.paymentservice.domain.event

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OutboxEventTest {

    private val now = LocalDateTime.now()
    private val testOeid = 123L
    private val testEventType = "PaymentOrderCreated"
    private val testAggregateId = "paymentorder-123"
    private val testPayload = """{"paymentOrderId":"123","amount":10000}"""
    private val testCreatedAt = now

    @Test
    fun `createNew should create OutboxEvent with NEW status`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        assertEquals(testOeid, outboxEvent.oeid)
        assertEquals(testEventType, outboxEvent.eventType)
        assertEquals(testAggregateId, outboxEvent.aggregateId)
        assertEquals(testPayload, outboxEvent.payload)
        assertEquals(OutboxEvent.Status.NEW, outboxEvent.status)
        assertEquals(testCreatedAt, outboxEvent.createdAt)
    }

    @Test
    fun `markAsProcessing should change status from NEW to PROCESSING`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        outboxEvent.markAsProcessing()

        assertEquals(OutboxEvent.Status.PROCESSING, outboxEvent.status)
        assertEquals(testOeid, outboxEvent.oeid)
        assertEquals(testEventType, outboxEvent.eventType)
        assertEquals(testAggregateId, outboxEvent.aggregateId)
        assertEquals(testPayload, outboxEvent.payload)
        assertEquals(testCreatedAt, outboxEvent.createdAt)
    }

    @Test
    fun `markAsSent should change status from PROCESSING to SENT`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        outboxEvent.markAsProcessing()
        outboxEvent.markAsSent()

        assertEquals(OutboxEvent.Status.SENT, outboxEvent.status)
    }

    @Test
    fun `markAsSent should change status from NEW to SENT`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        outboxEvent.markAsSent()

        assertEquals(OutboxEvent.Status.SENT, outboxEvent.status)
    }

    @Test
    fun `markAsProcessing should throw exception when status is not NEW`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        outboxEvent.markAsProcessing()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            outboxEvent.markAsProcessing()
        }

        assertTrue(exception.message!!.contains("Expected status NEW, was PROCESSING"))
    }

    @Test
    fun `markAsSent should throw exception when status is SENT`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        outboxEvent.markAsSent()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            outboxEvent.markAsSent()
        }

        assertTrue(exception.message!!.contains("Expected status NEW or PROCESSING"))
    }

    @Test
    fun `restore should recreate OutboxEvent with provided status`() {
        val outboxEvent = OutboxEvent.restore(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            status = "PROCESSING",
            createdAt = testCreatedAt
        )

        assertEquals(testOeid, outboxEvent.oeid)
        assertEquals(testEventType, outboxEvent.eventType)
        assertEquals(testAggregateId, outboxEvent.aggregateId)
        assertEquals(testPayload, outboxEvent.payload)
        assertEquals(OutboxEvent.Status.PROCESSING, outboxEvent.status)
        assertEquals(testCreatedAt, outboxEvent.createdAt)
    }

    @Test
    fun `restore should handle SENT status`() {
        val outboxEvent = OutboxEvent.restore(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            status = "SENT",
            createdAt = testCreatedAt
        )

        assertEquals(OutboxEvent.Status.SENT, outboxEvent.status)
    }

    @Test
    fun `restore should handle NEW status`() {
        val outboxEvent = OutboxEvent.restore(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            status = "NEW",
            createdAt = testCreatedAt
        )

        assertEquals(OutboxEvent.Status.NEW, outboxEvent.status)
    }

    // State Transition Tests

    @Test
    fun `should support complete state transition NEW to PROCESSING to SENT`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        assertEquals(OutboxEvent.Status.NEW, outboxEvent.status)

        outboxEvent.markAsProcessing()
        assertEquals(OutboxEvent.Status.PROCESSING, outboxEvent.status)

        outboxEvent.markAsSent()
        assertEquals(OutboxEvent.Status.SENT, outboxEvent.status)
    }

    @Test
    fun `should support direct transition NEW to SENT`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        assertEquals(OutboxEvent.Status.NEW, outboxEvent.status)

        outboxEvent.markAsSent()
        assertEquals(OutboxEvent.Status.SENT, outboxEvent.status)
    }

    // Edge Cases

    @Test
    fun `should handle large oeid values`() {
        val largeOeid = 999999999L
        val outboxEvent = OutboxEvent.createNew(
            oeid = largeOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        assertEquals(largeOeid, outboxEvent.oeid)
    }

    @Test
    fun `should handle large payload values`() {
        val largePayload = "x".repeat(10000) // 10KB payload
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = largePayload,
            createdAt = testCreatedAt
        )

        assertEquals(largePayload, outboxEvent.payload)
    }

    @Test
    fun `should handle different event types`() {
        val eventTypes = listOf(
            "PaymentOrderCreated",
            "PaymentOrderSucceeded",
            "PaymentOrderFailed",
            "PaymentOrderCaptureCommand",
            "PaymentOrderStatusCheckRequested"
        )

        eventTypes.forEach { eventType ->
            val outboxEvent = OutboxEvent.createNew(
                oeid = testOeid,
                eventType = eventType,
                aggregateId = testAggregateId,
                payload = testPayload,
                createdAt = testCreatedAt
            )

            assertEquals(eventType, outboxEvent.eventType)
        }
    }

    @Test
    fun `should handle different aggregate IDs`() {
        val aggregateIds = listOf(
            "paymentorder-123",
            "payment-456",
            "order-789",
            "user-999"
        )

        aggregateIds.forEach { aggregateId ->
            val outboxEvent = OutboxEvent.createNew(
                oeid = testOeid,
                eventType = testEventType,
                aggregateId = aggregateId,
                payload = testPayload,
                createdAt = testCreatedAt
            )

            assertEquals(aggregateId, outboxEvent.aggregateId)
        }
    }

    @Test
    fun `should handle JSON payload correctly`() {
        val jsonPayload = """
            {
                "paymentOrderId": "123",
                "amount": 10000,
                "currency": "USD",
                "status": "INITIATED_PENDING",
                "sellerId": "seller-789"
            }
        """.trimIndent()

        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = jsonPayload,
            createdAt = testCreatedAt
        )

        assertEquals(jsonPayload, outboxEvent.payload)
    }

    @Test
    fun `should handle empty payload`() {
        val emptyPayload = ""
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = emptyPayload,
            createdAt = testCreatedAt
        )

        assertEquals(emptyPayload, outboxEvent.payload)
    }

    @Test
    fun `should handle null payload`() {
        val nullPayload: String? = null
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = nullPayload ?: "",
            createdAt = testCreatedAt
        )

        assertEquals("", outboxEvent.payload)
    }

    // Immutability Tests

    @Test
    fun `should preserve immutability of non-status fields`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        val originalOeid = outboxEvent.oeid
        val originalEventType = outboxEvent.eventType
        val originalAggregateId = outboxEvent.aggregateId
        val originalPayload = outboxEvent.payload
        val originalCreatedAt = outboxEvent.createdAt

        outboxEvent.markAsProcessing()
        outboxEvent.markAsSent()

        assertEquals(originalOeid, outboxEvent.oeid)
        assertEquals(originalEventType, outboxEvent.eventType)
        assertEquals(originalAggregateId, outboxEvent.aggregateId)
        assertEquals(originalPayload, outboxEvent.payload)
        assertEquals(originalCreatedAt, outboxEvent.createdAt)
    }

    // Error Handling Tests

    @Test
    fun `markAsProcessing should provide clear error message for invalid status`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        outboxEvent.markAsProcessing()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            outboxEvent.markAsProcessing()
        }

        assertEquals("Expected status NEW, was PROCESSING", exception.message)
    }

    @Test
    fun `markAsSent should provide clear error message for invalid status`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = testOeid,
            eventType = testEventType,
            aggregateId = testAggregateId,
            payload = testPayload,
            createdAt = testCreatedAt
        )

        outboxEvent.markAsSent()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            outboxEvent.markAsSent()
        }

        assertTrue(exception.message!!.contains("Expected status NEW or PROCESSING"))
    }
}
