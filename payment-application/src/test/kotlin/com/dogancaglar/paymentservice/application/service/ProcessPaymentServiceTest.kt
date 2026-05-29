package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ProcessPaymentServiceTest {

    private lateinit var eventPublisher: EventPublisherPort
    private lateinit var retryQueuePort: RetryQueuePort<PaymentOrderCaptureCommand>
    private lateinit var paymentOrderModificationPort: PaymentOrderModificationPort
    private lateinit var accountDirectory: AccountDirectoryPort
    private lateinit var paymentTxPort: PaymentTxPort
    private lateinit var ledgerWritePort: LedgerEntryPort
    private lateinit var idGeneratorPort: IdGeneratorPort
    private lateinit var service: ProcessPaymentService

    @BeforeEach
    fun setUp() {
        eventPublisher = mockk()
        retryQueuePort = mockk(relaxed = true)
        paymentOrderModificationPort = mockk()
        accountDirectory = mockk(relaxed = true)
        paymentTxPort = mockk(relaxed = true)
        ledgerWritePort = mockk(relaxed = true)
        idGeneratorPort = mockk(relaxed = true)
        
        mockkObject(EventLogContext)
        every { EventLogContext.getEventId() } returns null
        every { EventLogContext.getTraceId() } returns "test-trace-id"
        // Mock aggregateId - in real usage this comes from EventLogContext.with(envelope)
        every { EventLogContext.getAggregateId() } returns null

        service = ProcessPaymentService(
            eventPublisher = eventPublisher,
            retryQueuePort = retryQueuePort,
            paymentOrderModificationPort = paymentOrderModificationPort,
            accountDirectory = accountDirectory,
            paymentTxPort = paymentTxPort,
            ledgerWritePort = ledgerWritePort,
            idGeneratorPort = idGeneratorPort
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkObject(EventLogContext)
    }
}

