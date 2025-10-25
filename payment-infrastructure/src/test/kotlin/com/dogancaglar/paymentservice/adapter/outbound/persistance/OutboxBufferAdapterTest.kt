package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.OutboxEventMapper
import com.dogancaglar.paymentservice.domain.event.OutboxEvent
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * Unit tests for OutboxBufferAdapter using MockK.
 * 
 * Tests verify:
 * - Domain ↔ Entity mapping logic
 * - Repository methods delegate correctly to mapper
 * - Batch operations handling
 * - Error handling and edge cases
 */
class OutboxBufferAdapterTest {

    private lateinit var outboxEventMapper: OutboxEventMapper
    private lateinit var adapter: OutboxBufferAdapter

    @BeforeEach
    fun setUp() {
        outboxEventMapper = mockk(relaxed = true)
        adapter = OutboxBufferAdapter(outboxEventMapper)
    }

    // ==================== findByStatus Tests ====================

    @Test
    fun `findByStatus should convert entities to domain objects`() {
        // Given
        val status = "NEW"
        val entities = listOf(
            createTestOutboxEventEntity(1L, status),
            createTestOutboxEventEntity(2L, status)
        )
        every { outboxEventMapper.findByStatus(status) } returns entities

        // When
        val result = adapter.findByStatus(status)

        // Then
        assertEquals(2, result.size)
        assertEquals(1L, result[0].oeid)
        assertEquals(2L, result[1].oeid)
        assertEquals(status, result[0].status.toString())
        assertEquals(status, result[1].status.toString())
        verify(exactly = 1) { outboxEventMapper.findByStatus(status) }
    }

    @Test
    fun `findByStatus should return empty list when no events found`() {
        // Given
        val status = "SENT"
        every { outboxEventMapper.findByStatus(status) } returns emptyList()

        // When
        val result = adapter.findByStatus(status)

        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 1) { outboxEventMapper.findByStatus(status) }
    }

    @Test
    fun `findByStatus should handle different statuses`() {
        // Given
        val statuses = listOf("NEW", "PROCESSING", "SENT", "FAILED")
        every { outboxEventMapper.findByStatus(any()) } returns emptyList()

        // When/Then
        statuses.forEach { status ->
            adapter.findByStatus(status)
            verify { outboxEventMapper.findByStatus(status) }
        }
    }

    // ==================== saveAll Tests ====================

    @Test
    fun `saveAll should convert domain objects to entities and call mapper`() {
        // Given
        val events = listOf(
            createTestOutboxEvent(1L),
            createTestOutboxEvent(2L),
            createTestOutboxEvent(3L)
        )
        every { outboxEventMapper.insertAllOutboxEvents(any()) } returns 1

        // When
        val result = adapter.saveAll(events)

        // Then
        assertEquals(3, result.size)
        assertEquals(1L, result[0].oeid)
        assertEquals(2L, result[1].oeid)
        assertEquals(3L, result[2].oeid)
        verify(exactly = 1) { 
            outboxEventMapper.insertAllOutboxEvents(
                match { entities ->
                    entities.size == 3 &&
                    entities[0].oeid == 1L &&
                    entities[1].oeid == 2L &&
                    entities[2].oeid == 3L
                }
            )
        }
    }

    @Test
    fun `saveAll should handle empty list`() {
        // Given
        val emptyList = emptyList<OutboxEvent>()

        // When
        val result = adapter.saveAll(emptyList)

        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 0) { outboxEventMapper.insertAllOutboxEvents(any()) }
    }

    @Test
    fun `saveAll should handle single event`() {
        // Given
        val event = createTestOutboxEvent(123L)
        every { outboxEventMapper.insertAllOutboxEvents(any()) } returns 1

        // When
        val result = adapter.saveAll(listOf(event))

        // Then
        assertEquals(1, result.size)
        assertEquals(123L, result[0].oeid)
        verify(exactly = 1) { 
            outboxEventMapper.insertAllOutboxEvents(
                match { entities -> entities.size == 1 && entities[0].oeid == 123L }
            )
        }
    }

    // ==================== save Tests ====================

    @Test
    fun `save should convert domain object to entity and call mapper`() {
        // Given
        val event = createTestOutboxEvent(456L)
        every { outboxEventMapper.insertOutboxEvent(any()) } returns 1

        // When
        val result = adapter.save(event)

        // Then
        assertEquals(456L, result.oeid)
        assertEquals(event.eventType, result.eventType)
        assertEquals(event.aggregateId, result.aggregateId)
        assertEquals(event.payload, result.payload)
        assertEquals(event.status, result.status)
        verify(exactly = 1) { 
            outboxEventMapper.insertOutboxEvent(
                match { entity ->
                    entity.oeid == event.oeid &&
                    entity.eventType == event.eventType &&
                    entity.aggregateId == event.aggregateId &&
                    entity.payload == event.payload &&
                    entity.status == event.status.toString()
                }
            )
        }
    }

    @Test
    fun `save should handle mapper exceptions`() {
        // Given
        val event = createTestOutboxEvent(789L)
        every { outboxEventMapper.insertOutboxEvent(any()) } throws RuntimeException("Database error")

        // When/Then
        assertThrows<RuntimeException> {
            adapter.save(event)
        }
    }

    // ==================== updateAll Tests ====================

    @Test
    fun `updateAll should convert domain objects to entities and call mapper`() {
        // Given
        val events = listOf(
            createTestOutboxEvent(1L, status = "PROCESSING"),
            createTestOutboxEvent(2L, status = "SENT")
        )
        every { outboxEventMapper.batchUpdate(any()) } returns 1

        // When
        adapter.updateAll(events)

        // Then
        verify(exactly = 1) { 
            outboxEventMapper.batchUpdate(
                match { entities ->
                    entities.size == 2 &&
                    entities[0].oeid == 1L &&
                    entities[0].status == "PROCESSING" &&
                    entities[1].oeid == 2L &&
                    entities[1].status == "SENT"
                }
            )
        }
    }

    @Test
    fun `updateAll should handle empty list`() {
        // Given
        val emptyList = emptyList<OutboxEvent>()

        // When
        adapter.updateAll(emptyList)

        // Then
        verify(exactly = 0) { outboxEventMapper.batchUpdate(any()) }
    }

    @Test
    fun `updateAll should handle single event`() {
        // Given
        val event = createTestOutboxEvent(999L, status = "SENT")
        every { outboxEventMapper.batchUpdate(any()) } returns 1

        // When
        adapter.updateAll(listOf(event))

        // Then
        verify(exactly = 1) { 
            outboxEventMapper.batchUpdate(
                match { entities -> 
                    entities.size == 1 && 
                    entities[0].oeid == 999L && 
                    entities[0].status == "SENT"
                }
            )
        }
    }

    // ==================== countByStatus Tests ====================

    @Test
    fun `countByStatus should delegate to mapper`() {
        // Given
        val status = "NEW"
        every { outboxEventMapper.countByStatus(status) } returns 5L

        // When
        val count = adapter.countByStatus(status)

        // Then
        assertEquals(5L, count)
        verify(exactly = 1) { outboxEventMapper.countByStatus(status) }
    }

    @Test
    fun `countByStatus should return zero when no events found`() {
        // Given
        val status = "SENT"
        every { outboxEventMapper.countByStatus(status) } returns 0L

        // When
        val count = adapter.countByStatus(status)

        // Then
        assertEquals(0L, count)
    }

    @Test
    fun `countByStatus should handle different statuses`() {
        // Given
        val statusCounts = mapOf(
            "NEW" to 10L,
            "PROCESSING" to 3L,
            "SENT" to 25L,
            "FAILED" to 1L
        )
        statusCounts.forEach { (status, count) ->
            every { outboxEventMapper.countByStatus(status) } returns count
        }

        // When/Then
        statusCounts.forEach { (status, expectedCount) ->
            val actualCount = adapter.countByStatus(status)
            assertEquals(expectedCount, actualCount)
            verify { outboxEventMapper.countByStatus(status) }
        }
    }

    // ==================== findBatchForDispatch Tests ====================

    @Test
    fun `findBatchForDispatch should throw IllegalStateException`() {
        // Given
        val batchSize = 10
        val workerId = "worker-1"

        // When/Then
        assertThrows<IllegalStateException> {
            adapter.findBatchForDispatch(batchSize, workerId)
        }
    }

    // ==================== Domain ↔ Entity Mapping Tests ====================

    @Test
    fun `should correctly map domain object to entity for save`() {
        // Given
        val event = createTestOutboxEventWithAllFields()
        every { outboxEventMapper.insertOutboxEvent(any()) } returns 1

        // When
        adapter.save(event)

        // Then
        verify(exactly = 1) { 
            outboxEventMapper.insertOutboxEvent(
                match { entity ->
                    entity.oeid == event.oeid &&
                    entity.eventType == event.eventType &&
                    entity.aggregateId == event.aggregateId &&
                    entity.payload == event.payload &&
                    entity.status == event.status.toString() &&
                    entity.createdAt == event.createdAt
                }
            )
        }
    }

    @Test
    fun `should correctly map entity to domain object for findByStatus`() {
        // Given
        val entity = createTestOutboxEventEntityWithAllFields()
        every { outboxEventMapper.findByStatus(any()) } returns listOf(entity)

        // When
        val result = adapter.findByStatus("NEW")

        // Then
        assertEquals(1, result.size)
        val domainObject = result[0]
        assertEquals(entity.oeid, domainObject.oeid)
        assertEquals(entity.eventType, domainObject.eventType)
        assertEquals(entity.aggregateId, domainObject.aggregateId)
        assertEquals(entity.payload, domainObject.payload)
        assertEquals(entity.status, domainObject.status.toString())
        assertEquals(entity.createdAt, domainObject.createdAt)
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    fun `should handle events with special characters in payload`() {
        // Given
        val specialPayload = """{"message": "Test with special chars: \"quotes\", 'apostrophes', \n newlines, \t tabs, \\ backslashes"}"""
        val event = OutboxEvent.restore(
            oeid = 1L,
            eventType = "test_event",
            aggregateId = "test_aggregate",
            payload = specialPayload,
            status = "PROCESSING",
            createdAt = LocalDateTime.now()
        )
        every { outboxEventMapper.insertOutboxEvent(any()) } returns 1

        // When
        adapter.save(event)

        // Then
        verify(exactly = 1) { 
            outboxEventMapper.insertOutboxEvent(
                match { entity -> entity.payload == specialPayload }
            )
        }
    }

    @Test
    fun `should handle events with unicode characters`() {
        // Given
        val unicodePayload = """{"message": "Test with unicode: 中文, العربية, русский, 🚀 emoji"}"""
        val event = OutboxEvent.restore(
            oeid = 2L,
            eventType = "test_event",
            aggregateId = "aggregate-测试-123",
            payload = unicodePayload,
            status = "PROCESSING",
            createdAt = LocalDateTime.now()
        )
        every { outboxEventMapper.insertOutboxEvent(any()) } returns 1

        // When
        adapter.save(event)

        // Then
        verify(exactly = 1) { 
            outboxEventMapper.insertOutboxEvent(
                match { entity -> 
                    entity.payload == unicodePayload &&
                    entity.aggregateId == "aggregate-测试-123"
                }
            )
        }
    }

    @Test
    fun `should handle large batch operations`() {
        // Given
        val largeBatch = (1L..1000L).map { createTestOutboxEvent(it) }
        every { outboxEventMapper.insertAllOutboxEvents(any()) } returns 1

        // When
        val result = adapter.saveAll(largeBatch)

        // Then
        assertEquals(1000, result.size)
        verify(exactly = 1) { 
            outboxEventMapper.insertAllOutboxEvents(
                match { entities -> entities.size == 1000 }
            )
        }
    }

    @Test
    fun `should handle events with different statuses in batch update`() {
        // Given
        val events = listOf(
            createTestOutboxEvent(1L, status = "NEW"),
            createTestOutboxEvent(2L, status = "PROCESSING"),
            createTestOutboxEvent(3L, status = "SENT"),
            createTestOutboxEvent(4L, status = "SENT")
        )
        every { outboxEventMapper.batchUpdate(any()) } returns 1

        // When
        adapter.updateAll(events)

        // Then
        verify(exactly = 1) { 
            outboxEventMapper.batchUpdate(
                match { entities ->
                    entities.size == 4 &&
                    entities[0].status == "NEW" &&
                    entities[1].status == "PROCESSING" &&
                    entities[2].status == "SENT" &&
                    entities[3].status == "SENT"
                }
            )
        }
    }

    // ==================== Helper Methods ====================

    private fun createTestOutboxEvent(
        oeid: Long,
        status: String = "NEW"
    ): OutboxEvent {
        return OutboxEvent.restore(
            oeid = oeid,
            eventType = "payment_order_created",
            aggregateId = "agg-$oeid",
            payload = """{"paymentOrderId": "$oeid", "amount": 10000}""",
            status = status,
            createdAt = LocalDateTime.now()
        )
    }

    private fun createTestOutboxEventWithAllFields(): OutboxEvent {
        return OutboxEvent.restore(
            oeid = 456L,
            eventType = "payment_order_psp_call_requested",
            aggregateId = "agg-456",
            payload = """{"paymentOrderId": "456", "amount": 25000, "currency": "EUR"}""",
            status = "PROCESSING",
            createdAt = LocalDateTime.of(2023, 6, 15, 10, 30)
        )
    }

    private fun createTestOutboxEventEntity(
        oeid: Long,
        status: String = "NEW"
    ): OutboxEventEntity {
        return OutboxEventEntity(
            oeid = oeid,
            eventType = "payment_order_created",
            aggregateId = "agg-$oeid",
            payload = """{"paymentOrderId": "$oeid", "amount": 10000}""",
            status = status,
            createdAt = LocalDateTime.now()
        )
    }

    private fun createTestOutboxEventEntityWithAllFields(): OutboxEventEntity {
        return OutboxEventEntity(
            oeid = 456L,
            eventType = "payment_order_psp_call_requested",
            aggregateId = "agg-456",
            payload = """{"paymentOrderId": "456", "amount": 25000, "currency": "EUR"}""",
            status = "PROCESSING",
            createdAt = LocalDateTime.of(2023, 6, 15, 10, 30)
        )
    }
}
