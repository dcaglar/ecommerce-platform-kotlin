package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.inbound.RecordLedgerEntriesUseCase
import io.mockk.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class LedgerRecordingConsumerTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var recordLedgerEntriesUseCase: RecordLedgerEntriesUseCase
    private lateinit var clock: Clock
    private lateinit var consumer: LedgerRecordingConsumer

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk()
        recordLedgerEntriesUseCase = mockk()
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)
        
        this.consumer = LedgerRecordingConsumer(
            kafkaTx = kafkaTxExecutor,
            recordLedgerEntriesUseCase = recordLedgerEntriesUseCase
        )
    }

    // ==================== Test 1: Successful Recording ====================

    @Test
    fun `should record ledger entries for SUCCESSFUL_FINAL command`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-123"
        val consumedEventId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val parentEventId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val expectedCreatedAt = LocalDateTime.now(clock)
        
        val command = LedgerRecordingCommand(
            paymentOrderId = paymentOrderId.value.toString(),
            paymentId = PaymentId(456L).value.toString(),
            sellerId = SellerId("seller-789").value,
            amountValue = 10000L,
            currency = "EUR",
            status = PaymentOrderStatus.CAPTURED.name,
            createdAt = expectedCreatedAt,
            updatedAt = expectedCreatedAt
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = command,
            eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord(
            "ledger-record-request-queue",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        // Mock LogContext
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { recordLedgerEntriesUseCase.recordLedgerEntries(any()) } returns Unit

        // When
        val kafkaConsumer = mockk<Consumer<*, *>>()
        every { kafkaConsumer.groupMetadata() } returns mockk()
        consumer.onLedgerRequested(record, kafkaConsumer)

        // Then - verify use case called with exact command
        verify(exactly = 1) {
            recordLedgerEntriesUseCase.recordLedgerEntries(
                event = match { cmd ->
                    cmd is LedgerRecordingCommand &&
                    cmd.paymentOrderId == paymentOrderId.value.toString() &&
                    cmd.paymentId == PaymentId(456L).value.toString() &&
                    cmd.sellerId == SellerId("seller-789").value &&
                    cmd.amountValue == 10000L &&
                    cmd.currency == "EUR" &&
                    cmd.status == PaymentOrderStatus.CAPTURED.name
                }
            )
        }
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<LedgerRecordingCommand>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == consumedEventId &&
                    env.aggregateId == paymentOrderId.value.toString() &&
                    env.traceId == expectedTraceId &&
                    env.parentEventId == parentEventId
                },
                any(),  // additionalContext (defaults to emptyMap)
                any()   // block lambda
            )
        }
    }

    // ==================== Test 2: Failed Recording Command ====================

    @Test
    fun `should record ledger entries for FAILED_FINAL command`() {
        // Given
        val paymentOrderId = PaymentOrderId(456L)
        val expectedTraceId = "trace-456"
        val consumedEventId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val parentEventId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        
        val command = LedgerRecordingCommand(
            paymentOrderId = paymentOrderId.value.toString(),
            paymentId = PaymentId(789L).value.toString(),
            sellerId = SellerId("seller-101").value,
            amountValue = 5000L,
            currency = "USD",
            status = PaymentOrderStatus.CAPTURE_FAILED.name,
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime()
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = command,
            eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord(
            "ledger-record-request-queue",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { recordLedgerEntriesUseCase.recordLedgerEntries(any()) } returns Unit

        // When
        val kafkaConsumer = mockk<Consumer<*, *>>()
        every { kafkaConsumer.groupMetadata() } returns mockk()
        consumer.onLedgerRequested(record, kafkaConsumer)

        // Then - verify use case called with failed command
        verify(exactly = 1) {
            recordLedgerEntriesUseCase.recordLedgerEntries(
                event = match { cmd ->
                    cmd is LedgerRecordingCommand &&
                    cmd.status == PaymentOrderStatus.CAPTURE_FAILED.name &&
                    cmd.amountValue == 5000L &&
                    cmd.currency == "USD"
                }
            )
        }
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<LedgerRecordingCommand>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == consumedEventId &&
                    env.aggregateId == paymentOrderId.value.toString() &&
                    env.traceId == expectedTraceId &&
                    env.parentEventId == parentEventId
                },
                any(),  // additionalContext (defaults to emptyMap)
                any()   // block lambda
            )
        }
    }

    // ==================== Test 3: Exception Handling ====================

    @Test
    fun `should propagate exception when recording fails`() {
        // Given
        val paymentOrderId = PaymentOrderId(789L)
        val command = LedgerRecordingCommand(
            paymentOrderId = paymentOrderId.value.toString(),
            paymentId = PaymentId(101L).value.toString(),
            sellerId = SellerId("seller-202").value,
            amountValue = 10000L,
            currency = "EUR",
            status = PaymentOrderStatus.CAPTURED.name,
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime()
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = command,
            eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-789",
            parentEventId = null
        )
        
        val record = ConsumerRecord(
            "ledger-record-request-queue",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { recordLedgerEntriesUseCase.recordLedgerEntries(any()) } throws RuntimeException("Database error")

        // When/Then - verify exception is propagated
        val kafkaConsumer = mockk<Consumer<*, *>>()
        every { kafkaConsumer.groupMetadata() } returns mockk()
        
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            consumer.onLedgerRequested(record, kafkaConsumer)
        }
        
        // Then - verify use case was called
        verify(exactly = 1) {
            recordLedgerEntriesUseCase.recordLedgerEntries(
                event = command
            )
        }
    }
}

