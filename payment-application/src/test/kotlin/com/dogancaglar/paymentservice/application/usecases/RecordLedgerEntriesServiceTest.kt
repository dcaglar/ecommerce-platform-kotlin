package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.ledger.AccountCategory
import com.dogancaglar.paymentservice.domain.model.ledger.AccountProfile
import com.dogancaglar.paymentservice.domain.model.ledger.AccountStatus
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class RecordLedgerEntriesServiceTest {

    private lateinit var ledgerWritePort: LedgerEntryPort
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var accountDirectory: AccountDirectoryPort
    private lateinit var clock: Clock
    private lateinit var service: RecordLedgerEntriesService

    @BeforeEach
    fun setUp() {
        ledgerWritePort = mockk()
        eventPublisherPort = mockk()
        accountDirectory = mockk()
        clock = Clock.fixed(Instant.parse("2024-05-01T10:00:00Z"), ZoneId.of("UTC"))

        service = RecordLedgerEntriesService(
            ledgerWritePort = ledgerWritePort,
            eventPublisherPort = eventPublisherPort,
            accountDirectory = accountDirectory,
            clock = clock
        )

        stubAccountProfiles()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `recordLedgerEntries emits LedgerEntriesRecorded for AUTHORIZED status`() {
        val command = sampleCommand(PaymentStatus.AUTHORIZED.name)
        val (eventId, traceId) = withMockedLogContext()

        val persistedEntriesSlot = slot<List<LedgerEntry>>()
        every { ledgerWritePort.postLedgerEntriesAtomic(capture(persistedEntriesSlot)) } answers {
            persistedEntriesSlot.captured.onEachIndexed { index, entry ->
                entry.ledgerEntryId = 1000L + index
            }
            persistedEntriesSlot.captured
        }

        val recordedEventSlot = slot<LedgerEntriesRecorded>()
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = capture(recordedEventSlot),
                parentEventId = eventId,
                traceId = traceId
            )
        } returns mockk()

        service.recordLedgerEntries(command)

        val authLedgerEntry = persistedEntriesSlot.captured.single()
        assertEquals("AUTH:${command.paymentId}", authLedgerEntry.journalEntry.id)
        assertEquals(2, authLedgerEntry.journalEntry.postings.size)

        val recordedEvent = recordedEventSlot.captured
        assertTrue(recordedEvent.ledgerBatchId.startsWith("ledger-batch-"))
        assertEquals(command.paymentOrderId, recordedEvent.paymentOrderId)
        assertEquals(1, recordedEvent.entryCount)

        val ledgerEntryEvent = recordedEvent.ledgerEntries.single()
        assertEquals(1000L, ledgerEntryEvent.ledgerEntryId)
        assertEquals("AUTH:${command.paymentId}", ledgerEntryEvent.journalEntryId)
        assertEquals("AUTH_RECEIVABLE.GLOBAL.${command.currency}", ledgerEntryEvent.postings[0].accountCode)
        assertEquals("AUTH_LIABILITY.GLOBAL.${command.currency}", ledgerEntryEvent.postings[1].accountCode)

        verify(exactly = 1) { ledgerWritePort.postLedgerEntriesAtomic(any()) }
        verify(exactly = 1) {
            eventPublisherPort.publishSync<LedgerEntriesRecorded>(
                preSetEventIdFromCaller = null,
                aggregateId = command.sellerId,
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                data = any(),
                traceId = traceId,
                parentEventId = eventId,
                timeoutSeconds = 5
            )
        }
        unmockkObject(LogContext)
    }

    @Test
    fun `recordLedgerEntries emits capture journal for CAPTURED status`() {
        val command = sampleCommand("CAPTURED")
        val (eventId, traceId) = withMockedLogContext()

        val persistedEntriesSlot = slot<List<LedgerEntry>>()
        every { ledgerWritePort.postLedgerEntriesAtomic(capture(persistedEntriesSlot)) } answers {
            persistedEntriesSlot.captured.forEachIndexed { index, entry ->
                entry.ledgerEntryId = 2000L + index
            }
            persistedEntriesSlot.captured
        }

        val recordedEventSlot = slot<LedgerEntriesRecorded>()
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = capture(recordedEventSlot),
                parentEventId = eventId,
                traceId = traceId
            )
        } returns mockk()

        service.recordLedgerEntries(command)

        val captureEntry = persistedEntriesSlot.captured.single()
        assertEquals("CAPTURE:${command.paymentOrderId}", captureEntry.journalEntry.id)
        assertEquals(4, captureEntry.journalEntry.postings.size)

        val event = recordedEventSlot.captured
        assertEquals(1, event.entryCount)
        assertEquals("CAPTURE:${command.paymentOrderId}", event.ledgerEntries.single().journalEntryId)

        val postings = event.ledgerEntries.single().postings
        val codes = postings.map { it.accountCode }.toSet()
        assertEquals(
            setOf(
                "AUTH_RECEIVABLE.GLOBAL.${command.currency}",
                "AUTH_LIABILITY.GLOBAL.${command.currency}",
                "MERCHANT_PAYABLE.${command.sellerId}.${command.currency}",
                "PSP_RECEIVABLES.GLOBAL.${command.currency}"
            ),
            codes
        )

        verify(exactly = 1) { ledgerWritePort.postLedgerEntriesAtomic(any()) }
        verify(exactly = 1) {
            eventPublisherPort.publishSync<LedgerEntriesRecorded>(
                preSetEventIdFromCaller = null,
                aggregateId = command.sellerId,
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                data = any(),
                traceId = traceId,
                parentEventId = eventId,
                timeoutSeconds = 5
            )
        }
        unmockkObject(LogContext)
    }

    @Test
    fun `recordLedgerEntries skips publishing when journal entries are empty`() {
        listOf("FAILED_FINAL", "FAILED").forEach { status ->
            service.recordLedgerEntries(sampleCommand(status))
        }

        verify { ledgerWritePort wasNot Called }
        verify { eventPublisherPort wasNot Called }
    }

    @Test
    fun `recordLedgerEntries returns when persistence yields no entries`() {
        val command = sampleCommand(PaymentStatus.AUTHORIZED.name)
        every { ledgerWritePort.postLedgerEntriesAtomic(any()) } returns emptyList()

        service.recordLedgerEntries(command)

        verify(exactly = 1) { ledgerWritePort.postLedgerEntriesAtomic(any()) }
        verify { eventPublisherPort wasNot Called }
    }

    @Test
    fun `recordLedgerEntries propagates publish failure after persisting`() {
        val command = sampleCommand(PaymentStatus.AUTHORIZED.name)
        val (eventId, traceId) = withMockedLogContext()

        val persistedEntriesSlot = slot<List<LedgerEntry>>()
        every { ledgerWritePort.postLedgerEntriesAtomic(capture(persistedEntriesSlot)) } answers {
            persistedEntriesSlot.captured.forEachIndexed { index, entry ->
                entry.ledgerEntryId = 42L + index
            }
            persistedEntriesSlot.captured
        }
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = any(),
                parentEventId = eventId,
                traceId = traceId
            )
        } throws RuntimeException("publish failed")

        val thrown = org.junit.jupiter.api.assertThrows<RuntimeException> {
            service.recordLedgerEntries(command)
        }
        assertEquals("publish failed", thrown.message)
        unmockkObject(LogContext)

        verify(exactly = 1) { ledgerWritePort.postLedgerEntriesAtomic(any()) }
        verify(exactly = 1) {
            eventPublisherPort.publishSync<LedgerEntriesRecorded>(
                preSetEventIdFromCaller = null,
                aggregateId = command.sellerId,
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                data = any(),
                traceId = traceId,
                parentEventId = eventId,
                timeoutSeconds = 5
            )
        }
    }

    private fun sampleCommand(status: String) = LedgerRecordingCommand(
        paymentOrderId = "po-123",
        paymentId = "pay-456",
        sellerId = "seller-789",
        amountValue = 10_000L,
        currency = "EUR",
        status = status,
        createdAt = LocalDateTime.now(clock),
        updatedAt = LocalDateTime.now(clock)
    )

    private fun withMockedLogContext(): Pair<UUID, String> {
        val expectedEventId = UUID.randomUUID()
        val expectedTraceId = "trace-${expectedEventId.toString().take(8)}"
        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId
        return expectedEventId to expectedTraceId
    }

    private fun stubAccountProfiles() {
        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, any()) } answers {
            val sellerId = arg<String>(1)
            AccountProfile(
                accountCode = "MERCHANT_PAYABLE.$sellerId.EUR",
                type = AccountType.MERCHANT_PAYABLE,
                entityId = sellerId,
                currency = Currency("EUR"),
                category = AccountCategory.LIABILITY,
                country = null,
                status = AccountStatus.ACTIVE
            )
        }
        every { accountDirectory.getAccountProfile(AccountType.AUTH_RECEIVABLE, "GLOBAL") } returns accountProfile(
            code = "AUTH_RECEIVABLE.GLOBAL.EUR",
            type = AccountType.AUTH_RECEIVABLE
        )
        every { accountDirectory.getAccountProfile(AccountType.AUTH_LIABILITY, "GLOBAL") } returns accountProfile(
            code = "AUTH_LIABILITY.GLOBAL.EUR",
            type = AccountType.AUTH_LIABILITY,
            category = AccountCategory.LIABILITY
        )
        every { accountDirectory.getAccountProfile(AccountType.PSP_RECEIVABLES, "GLOBAL") } returns accountProfile(
            code = "PSP_RECEIVABLES.GLOBAL.EUR",
            type = AccountType.PSP_RECEIVABLES
        )
    }

    private fun accountProfile(
        code: String,
        type: AccountType,
        category: AccountCategory = type.category
    ) = AccountProfile(
        accountCode = code,
        type = type,
        entityId = code.substringAfter('.').substringBefore('.'),
        currency = Currency("EUR"),
        category = category,
        country = null,
        status = AccountStatus.ACTIVE
    )
}

