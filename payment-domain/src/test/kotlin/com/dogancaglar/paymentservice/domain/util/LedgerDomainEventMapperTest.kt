package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.event.LedgerEntryEventData
import com.dogancaglar.paymentservice.domain.event.PostingDirection
import com.dogancaglar.paymentservice.domain.event.PostingEventData
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Posting
import com.dogancaglar.paymentservice.domain.util.LedgerDomainEventMapper.toDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class LedgerDomainEventMapperTest {

    private val fixedInstant = Instant.parse("2025-11-07T16:20:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val ledgerEntryFactory = LedgerEntryFactory(clock)

    private fun sampleLedgerEntry(): LedgerEntry {
        val merchantAccount = Account.mock(AccountType.MERCHANT_PAYABLE, "SELLER-333", "EUR")
        val pspReceivable = Account.mock(AccountType.PSP_RECEIVABLES, "GLOBAL", "EUR")
        val debitPosting = Posting.Debit.create(merchantAccount, Amount.of(1000, Currency("EUR")))
        val creditPosting = Posting.Credit.create(pspReceivable, Amount.of(1000, Currency("EUR")))

        val journal = JournalEntry.fromPersistence(
            id = "journal-123",
            txType = JournalType.CAPTURE,
            name = "capture",

            postings = listOf(debitPosting, creditPosting)
        )

        return ledgerEntryFactory.fromPersistence(
            ledgerEntryId = 1234L,
            journalEntry = journal,
            createdAt = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC)
        )
    }

    private fun sampleLedgerEntryEventData(): LedgerEntryEventData {
        val debitEvent = PostingEventData.create(
            accountCode = "MERCHANT_PAYABLE.SELLER-333.EUR",
            accountType = AccountType.MERCHANT_PAYABLE,
            amount = 1000,
            currency = "EUR",
            direction = PostingDirection.DEBIT
        )
        val creditEvent = PostingEventData.create(
            accountCode = "PSP_RECEIVABLES.GLOBAL.EUR",
            accountType = AccountType.PSP_RECEIVABLES,
            amount = 1000,
            currency = "EUR",
            direction = PostingDirection.CREDIT
        )

        return LedgerEntryEventData.create(
            ledgerEntryId = 9876L,
            journalEntryId = "journal-9876",
            journalType = JournalType.CAPTURE,
            journalName = "capture",
            createdAt = LocalDateTime.ofInstant(fixedInstant.plusSeconds(2400), ZoneOffset.UTC),
            postings = listOf(debitEvent, creditEvent)
        )
    }

    @Test
    fun `toLedgerEntryEventData maps domain ledger entry to event DTO`() {
        val ledgerEntry = sampleLedgerEntry()

        val eventData = LedgerDomainEventMapper.toLedgerEntryEventData(ledgerEntry)

        assertEquals(ledgerEntry.ledgerEntryId, eventData.ledgerEntryId)
        assertEquals(ledgerEntry.journalEntry.id, eventData.journalEntryId)
        assertEquals(ledgerEntry.journalEntry.txType, eventData.journalType)
        assertEquals(ledgerEntry.createdAt, eventData.createdAt)

        val postingEvent = eventData.postings.first()
        val postingDomain = ledgerEntry.journalEntry.postings.first()

        assertEquals(postingDomain.account.accountCode, postingEvent.accountCode)
        assertEquals(postingDomain.account.type, postingEvent.accountType)
        assertEquals(postingDomain.amount.quantity, postingEvent.amount)
        assertEquals(postingDomain.amount.currency.currencyCode, postingEvent.currency)
        assertEquals(PostingDirection.DEBIT, postingEvent.direction)
    }

    @Test
    fun `PostingEventData toDomain round-trips account entity without duplicating currency`() {
        val postingEvent = PostingEventData.create(
            accountCode = "MERCHANT_PAYABLE.SELLER-333.EUR",
            accountType = AccountType.MERCHANT_PAYABLE,
            amount = 1000,
            currency = "EUR",
            direction = PostingDirection.CREDIT
        )

        val postingDomain = postingEvent.toDomain()

        assertEquals("MERCHANT_PAYABLE.SELLER-333.EUR", postingDomain.account.accountCode)
        assertEquals("SELLER-333", postingDomain.account.entityId)
        assertEquals(AccountType.MERCHANT_PAYABLE, postingDomain.account.type)
        assertEquals("EUR", postingDomain.account.currency.currencyCode)
        assertTrue(postingDomain is Posting.Credit)
    }

    @Test
    fun `toDomain recreates ledger entry with postings`() {
        val eventData = sampleLedgerEntryEventData()

        val domain = LedgerDomainEventMapper.toDomain(eventData)

        assertEquals(eventData.ledgerEntryId, domain.ledgerEntryId)
        assertEquals(eventData.journalEntryId, domain.journalEntry.id)
        assertEquals(eventData.journalType, domain.journalEntry.txType)
        assertEquals(eventData.createdAt, domain.createdAt)

        val postings = domain.journalEntry.postings
        assertEquals(2, postings.size)

        val debitPosting = postings.first { it is Posting.Debit }
        assertEquals("MERCHANT_PAYABLE.SELLER-333.EUR", debitPosting.account.accountCode)
        assertEquals(AccountType.MERCHANT_PAYABLE, debitPosting.account.type)
        assertEquals("SELLER-333", debitPosting.account.entityId)
        assertEquals("EUR", debitPosting.account.currency.currencyCode)
        assertEquals(1000, debitPosting.amount.quantity)

        val creditPosting = postings.first { it is Posting.Credit }
        assertEquals("PSP_RECEIVABLES.GLOBAL.EUR", creditPosting.account.accountCode)
        assertEquals(AccountType.PSP_RECEIVABLES, creditPosting.account.type)
        assertEquals("GLOBAL", creditPosting.account.entityId)
        assertEquals("EUR", creditPosting.account.currency.currencyCode)
        assertEquals(1000, creditPosting.amount.quantity)
    }
}

