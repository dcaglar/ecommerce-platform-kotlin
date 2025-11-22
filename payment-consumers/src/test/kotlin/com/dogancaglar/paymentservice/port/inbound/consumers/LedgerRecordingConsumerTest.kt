package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog
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
import com.dogancaglar.common.time.Utc
import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder

class LedgerRecordingConsumerTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var recordLedgerEntriesUseCase: RecordLedgerEntriesUseCase
    private lateinit var consumer: LedgerRecordingConsumer

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk()
        recordLedgerEntriesUseCase = mockk()
        
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
        val consumedEventId = "11111111-1111-1111-1111-111111111111"
        val parentEventId = "22222222-2222-2222-2222-222222222222"
        val expectedCreatedAt = Utc.nowLocalDateTime()
        val paymentId = PaymentId(456L)
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-789"),
            amount = Amount.of(10000L, Currency("EUR")),
            status = PaymentOrderStatus.CAPTURED,
            retryCount = 0,
            createdAt = expectedCreatedAt,
            updatedAt = expectedCreatedAt
        )
        val finalizedEvent = PaymentOrderFinalized.from(paymentOrder, Utc.toInstant(expectedCreatedAt), PaymentOrderStatus.CAPTURED)
        val command = LedgerRecordingCommand.from(finalizedEvent, Utc.toInstant(expectedCreatedAt))
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = command,
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
        
        // Mock EventLogContext
        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
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
                    cmd.finalStatus == "payment_order_finalized" &&
                    cmd.publicPaymentOrderId == finalizedEvent.publicPaymentOrderId
                }
            )
        }
        
        // Verify EventLogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            EventLogContext.with<LedgerRecordingCommand>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == command.deterministicEventId() &&
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
        val consumedEventId = "33333333-3333-3333-3333-333333333333"
        val parentEventId = "44444444-4444-4444-4444-444444444444"
        val paymentId = PaymentId(789L)
        val now = Utc.nowLocalDateTime()
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-101"),
            amount = Amount.of(5000L, Currency("USD")),
            status = PaymentOrderStatus.CAPTURE_FAILED,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val finalizedEvent = PaymentOrderFinalized.from(paymentOrder, Utc.toInstant(now), PaymentOrderStatus.CAPTURE_FAILED)
        val command = LedgerRecordingCommand.from(finalizedEvent, Utc.toInstant(now))
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = command,
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
        
        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
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
                    cmd.finalStatus == "payment_order_finalized" &&
                    cmd.publicPaymentOrderId == finalizedEvent.publicPaymentOrderId &&
                    cmd.amountValue == 5000L &&
                    cmd.currency == "USD"
                }
            )
        }
        
        // Verify EventLogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            EventLogContext.with<LedgerRecordingCommand>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == command.deterministicEventId() &&
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
        val paymentId = PaymentId(101L)
        val now = Utc.nowLocalDateTime()
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-202"),
            amount = Amount.of(10000L, Currency("EUR")),
            status = PaymentOrderStatus.CAPTURED,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val finalizedEvent = PaymentOrderFinalized.from(paymentOrder, Utc.toInstant(now), PaymentOrderStatus.CAPTURED)
        val command = LedgerRecordingCommand.from(finalizedEvent, Utc.toInstant(now))
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = command,
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
        
        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
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

