package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceUseCase
import com.dogancaglar.paymentservice.util.LedgerEntriesRecordedTestHelper
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AccountBalanceConsumerTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var accountBalanceService: AccountBalanceUseCase
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var consumer: AccountBalanceConsumer

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk(relaxed = true)
        accountBalanceService = mockk(relaxed = true)
        meterRegistry = mockk(relaxed = true)
        
        every { meterRegistry.counter(any()) } returns mockk(relaxed = true)
        every { meterRegistry.timer(any()) } returns mockk(relaxed = true)
        
        consumer = AccountBalanceConsumer(kafkaTxExecutor, accountBalanceService, meterRegistry)
    }

    @Test
    fun `onLedgerEntriesRecorded should process batch and commit offsets atomically`() {
        // Given
        val traceId = "trace-123"
        val sellerId = "merchant-456"
        val currency = "USD"
        
        val ledgerEntriesRecorded = LedgerEntriesRecordedTestHelper.createLedgerEntriesRecorded(
            paymentOrderId = "po-123",
            publicPaymentOrderId = "paymentorder-123",
            sellerId = sellerId,
            currency = currency,
            recordedAt = LocalDateTime.now(),
            ledgerEntries = listOf(
                LedgerEntriesRecordedTestHelper.createSimpleLedgerEntryEventData(1001L, sellerId, currency),
                LedgerEntriesRecordedTestHelper.createSimpleLedgerEntryEventData(1002L, sellerId, currency)
            ),
            ledgerBatchId = "batch-001",
            traceId = traceId
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = ledgerEntriesRecorded,
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = sellerId,
            traceId = traceId
        )
        
        val record = ConsumerRecord(
            "ledger_entries_recorded_topic",
            0,
            100L,
            sellerId,
            envelope
        )
        
        val records = listOf(record)
        
        // Mock LogContext
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        // Mock KafkaTxExecutor to execute the lambda
        every { 
            kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) 
        } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { 
            accountBalanceService.updateAccountBalancesBatch(any())
        } returns listOf(1001L, 1002L)
        
        val kafkaConsumer = mockk<Consumer<*, *>>()
        every { kafkaConsumer.groupMetadata() } returns mockk()

        // When
        consumer.onLedgerEntriesRecorded(records, kafkaConsumer)

        // Then - Verify service was called with correct parameters
        verify(exactly = 1) {
            accountBalanceService.updateAccountBalancesBatch(
                match { entries ->
                    entries.size == 2 &&
                    entries[0].ledgerEntryId == 1001L &&
                    entries[1].ledgerEntryId == 1002L
                }
            )
        }
        
        // Verify KafkaTxExecutor was called for atomic offset commit
        verify(exactly = 1) {
            kafkaTxExecutor.run(
                match { offsets ->
                    offsets.containsKey(TopicPartition("ledger_entries_recorded_topic", 0)) &&
                    offsets[TopicPartition("ledger_entries_recorded_topic", 0)]?.offset() == 101L
                },
                any(),
                any()
            )
        }
    }

    @Test
    fun `onLedgerEntriesRecorded should skip processing when batch is empty`() {
        // Given
        val records = emptyList<ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>>()
        val kafkaConsumer = mockk<Consumer<*, *>>()

        // When
        consumer.onLedgerEntriesRecorded(records, kafkaConsumer)

        // Then
        verify(exactly = 0) { kafkaTxExecutor.run(any(), any(), any()) }
        verify(exactly = 0) { accountBalanceService.updateAccountBalancesBatch(any()) }
    }

    @Test
    fun `onLedgerEntriesRecorded should skip processing when no ledger entries in events`() {
        // Given
        val sellerId = "merchant-456"
        val ledgerEntriesRecorded = LedgerEntriesRecordedTestHelper.createLedgerEntriesRecorded(
            paymentOrderId = "po-123",
            publicPaymentOrderId = "paymentorder-123",
            sellerId = sellerId,
            currency = "USD",
            recordedAt = LocalDateTime.now(),
            ledgerEntries = emptyList(),
            ledgerBatchId = "batch-001",
            traceId = "trace-123"
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = ledgerEntriesRecorded,
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = sellerId,
            traceId = "trace-123"
        )
        
        val record = ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>(
            "ledger_entries_recorded_topic",
            0,
            100L,
            sellerId,
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { 
            kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) 
        } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        val kafkaConsumer = mockk<Consumer<*, *>>()
        every { kafkaConsumer.groupMetadata() } returns mockk()

        // When
        consumer.onLedgerEntriesRecorded(listOf(record), kafkaConsumer)

        // Then - Service should not be called
        verify(exactly = 0) { accountBalanceService.updateAccountBalancesBatch(any()) }
        
        // But offset should still be committed
        verify(exactly = 1) { kafkaTxExecutor.run(any(), any(), any()) }
    }

    @Test
    fun `onLedgerEntriesRecorded should handle already processed entries`() {
        // Given
        val sellerId = "merchant-456"
        val currency = "USD"
        val ledgerEntriesRecorded = LedgerEntriesRecordedTestHelper.createLedgerEntriesRecorded(
            paymentOrderId = "po-123",
            publicPaymentOrderId = "paymentorder-123",
            sellerId = sellerId,
            currency = currency,
            recordedAt = LocalDateTime.now(),
            ledgerEntries = listOf(
                LedgerEntriesRecordedTestHelper.createSimpleLedgerEntryEventData(1001L, sellerId, currency)
            ),
            ledgerBatchId = "batch-001",
            traceId = "trace-123"
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = ledgerEntriesRecorded,
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = sellerId,
            traceId = "trace-123"
        )
        
        val record = ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>(
            "ledger_entries_recorded_topic",
            0,
            100L,
            sellerId,
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { 
            kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) 
        } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        // Service returns empty list (already processed)
        every { 
            accountBalanceService.updateAccountBalancesBatch(any())
        } returns emptyList()
        
        val kafkaConsumer = mockk<Consumer<*, *>>()
        every { kafkaConsumer.groupMetadata() } returns mockk()

        // When
        consumer.onLedgerEntriesRecorded(listOf(record), kafkaConsumer)

        // Then - Service called but returned empty (idempotent skip)
        verify(exactly = 1) { 
            accountBalanceService.updateAccountBalancesBatch(any())
        }
        
        // Offset should still be committed
        verify(exactly = 1) { kafkaTxExecutor.run(any(), any(), any()) }
    }

    @Test
    fun `onLedgerEntriesRecorded should calculate correct offsets for multiple partitions with different sellerIds`() {
        // Given - Records from different partitions with different sellerIds
        // Note: Events are partitioned by sellerId (aggregateId), so different sellerIds 
        // will naturally go to different partitions, ensuring ordering per seller
        val sellerId1 = "merchant-456"
        val sellerId2 = "merchant-789"
        val currency = "USD"
        
        val ledgerEntriesRecorded1 = LedgerEntriesRecordedTestHelper.createLedgerEntriesRecorded(
            paymentOrderId = "po-123",
            publicPaymentOrderId = "paymentorder-123",
            sellerId = sellerId1,
            currency = currency,
            recordedAt = LocalDateTime.now(),
            ledgerEntries = listOf(
                LedgerEntriesRecordedTestHelper.createSimpleLedgerEntryEventData(1001L, sellerId1, currency)
            ),
            ledgerBatchId = "batch-001",
            traceId = "trace-123"
        )
        
        val ledgerEntriesRecorded2 = LedgerEntriesRecordedTestHelper.createLedgerEntriesRecorded(
            paymentOrderId = "po-456",
            publicPaymentOrderId = "paymentorder-456",
            sellerId = sellerId2,
            currency = currency,
            recordedAt = LocalDateTime.now(),
            ledgerEntries = listOf(
                LedgerEntriesRecordedTestHelper.createSimpleLedgerEntryEventData(1002L, sellerId2, currency)
            ),
            ledgerBatchId = "batch-002",
            traceId = "trace-456"
        )
        
        val envelope1 = DomainEventEnvelopeFactory.envelopeFor(
            data = ledgerEntriesRecorded1,
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = sellerId1,
            traceId = "trace-123"
        )
        
        val envelope2 = DomainEventEnvelopeFactory.envelopeFor(
            data = ledgerEntriesRecorded2,
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = sellerId2,
            traceId = "trace-456"
        )
        
        // Different sellerIds will be partitioned to different partitions
        val record1 = ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>(
            "ledger_entries_recorded_topic",
            0,
            100L,
            sellerId1, // Kafka key = sellerId (partitioning key)
            envelope1
        )
        
        val record2 = ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>(
            "ledger_entries_recorded_topic",
            1,
            50L,
            sellerId2, // Different sellerId → different partition
            envelope2
        )
        
        val records = listOf(record1, record2)
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        val capturedOffsets = slot<Map<TopicPartition, OffsetAndMetadata>>()
        every { 
            kafkaTxExecutor.run(capture(capturedOffsets), any(), any<() -> Unit>()) 
        } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { 
            accountBalanceService.updateAccountBalancesBatch(any())
        } returns listOf(1001L, 1002L)
        
        val kafkaConsumer = mockk<Consumer<*, *>>()
        every { kafkaConsumer.groupMetadata() } returns mockk()

        // When
        consumer.onLedgerEntriesRecorded(records, kafkaConsumer)

        // Then - Verify offsets for both partitions (different sellerIds → different partitions)
        val offsets = capturedOffsets.captured
        assertEquals(101L, offsets[TopicPartition("ledger_entries_recorded_topic", 0)]?.offset())
        assertEquals(51L, offsets[TopicPartition("ledger_entries_recorded_topic", 1)]?.offset())
    }

    @Test
    fun `onLedgerEntriesRecorded should group offset calculation by partition for multiple records in same partition`() {
        // Given - Multiple records from the same partition
        // Note: Partitioning happens at publish time (by sellerId). This test verifies that
        // the consumer correctly groups records by partition and calculates max offset per partition.
        val sellerId = "merchant-456"
        val currency = "USD"
        
        val ledgerEntriesRecorded1 = LedgerEntriesRecordedTestHelper.createLedgerEntriesRecorded(
            paymentOrderId = "po-123",
            publicPaymentOrderId = "paymentorder-123",
            sellerId = sellerId,
            currency = currency,
            recordedAt = LocalDateTime.now(),
            ledgerEntries = listOf(
                LedgerEntriesRecordedTestHelper.createSimpleLedgerEntryEventData(1001L, sellerId, currency)
            ),
            ledgerBatchId = "batch-001",
            traceId = "trace-123"
        )
        
        val ledgerEntriesRecorded2 = LedgerEntriesRecordedTestHelper.createLedgerEntriesRecorded(
            paymentOrderId = "po-124",
            publicPaymentOrderId = "paymentorder-124",
            sellerId = sellerId,
            currency = currency,
            recordedAt = LocalDateTime.now(),
            ledgerEntries = listOf(
                LedgerEntriesRecordedTestHelper.createSimpleLedgerEntryEventData(1002L, sellerId, currency)
            ),
            ledgerBatchId = "batch-002",
            traceId = "trace-124"
        )
        
        val envelope1 = DomainEventEnvelopeFactory.envelopeFor(
            data = ledgerEntriesRecorded1,
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = sellerId,
            traceId = "trace-123"
        )
        
        val envelope2 = DomainEventEnvelopeFactory.envelopeFor(
            data = ledgerEntriesRecorded2,
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = sellerId,
            traceId = "trace-124"
        )
        
        // Both records manually set to partition 0 (simulating what Kafka would do
        // when partitioning by sellerId - same sellerId goes to same partition)
        val record1 = ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>(
            "ledger_entries_recorded_topic",
            0,
            100L,
            sellerId,
            envelope1
        )
        
        val record2 = ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>(
            "ledger_entries_recorded_topic",
            0, // Same partition
            101L,
            sellerId,
            envelope2
        )
        
        val records = listOf(record1, record2)
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        val capturedOffsets = slot<Map<TopicPartition, OffsetAndMetadata>>()
        every { 
            kafkaTxExecutor.run(capture(capturedOffsets), any(), any<() -> Unit>()) 
        } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { 
            accountBalanceService.updateAccountBalancesBatch(any())
        } returns listOf(1001L, 1002L)
        
        val kafkaConsumer = mockk<Consumer<*, *>>()
        every { kafkaConsumer.groupMetadata() } returns mockk()

        // When
        consumer.onLedgerEntriesRecorded(records, kafkaConsumer)

        // Then - Verify offset calculation: records grouped by partition, max offset + 1 per partition
        val offsets = capturedOffsets.captured
        assertEquals(1, offsets.size, "Records from same partition should result in one offset entry")
        // Max offset (101) + 1 = 102
        assertEquals(102L, offsets[TopicPartition("ledger_entries_recorded_topic", 0)]?.offset())
    }

    @Test
    fun `onLedgerEntriesRecorded should not commit offsets on exception`() {
        // Given
        val sellerId = "merchant-456"
        val currency = "USD"
        val ledgerEntriesRecorded = LedgerEntriesRecordedTestHelper.createLedgerEntriesRecorded(
            paymentOrderId = "po-123",
            publicPaymentOrderId = "paymentorder-123",
            sellerId = sellerId,
            currency = currency,
            recordedAt = LocalDateTime.now(),
            ledgerEntries = listOf(
                LedgerEntriesRecordedTestHelper.createSimpleLedgerEntryEventData(1001L, sellerId, currency)
            ),
            ledgerBatchId = "batch-001",
            traceId = "trace-123"
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = ledgerEntriesRecorded,
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = sellerId,
            traceId = "trace-123"
        )
        
        val record = ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>(
            "ledger_entries_recorded_topic",
            0,
            100L,
            sellerId,
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        // KafkaTxExecutor throws exception (simulating failure)
        every { 
            kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) 
        } throws RuntimeException("Database connection failed")
        
        val kafkaConsumer = mockk<Consumer<*, *>>()
        every { kafkaConsumer.groupMetadata() } returns mockk()

        // When/Then - Exception should propagate
        try {
            consumer.onLedgerEntriesRecorded(listOf(record), kafkaConsumer)
            fail("Expected exception")
        } catch (e: RuntimeException) {
            assertEquals("Database connection failed", e.message)
        }
        
        // Service should not be called (exception before processing)
        verify(exactly = 0) { accountBalanceService.updateAccountBalancesBatch(any()) }
    }

}

