package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.paymentservice.application.events.LedgerEntryEventData
import com.dogancaglar.paymentservice.application.events.PostingDirection
import com.dogancaglar.paymentservice.application.events.PostingEventData
import com.dogancaglar.paymentservice.application.util.LedgerDomainEventMapper.toDomain
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Posting
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.util.LedgerEntryFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class LedgerDomainEventMapperTest {

    private val fixedInstant = Instant.parse("2025-11-07T16:20:00Z")
    private val ledgerEntryFactory = LedgerEntryFactory()

    private fun sampleLedgerEntry(): LedgerEntry {
        val merchantAccount = Account.Companion.mock(AccountType.MERCHANT_PAYABLE, "SELLER-333", "EUR")
        val pspReceivable = Account.Companion.mock(AccountType.PSP_RECEIVABLES, "GLOBAL", "EUR")
        val debitPosting = Posting.Debit.create(merchantAccount, Amount.Companion.of(1000, Currency("EUR")))
        val creditPosting = Posting.Credit.create(pspReceivable, Amount.Companion.of(1000, Currency("EUR")))

        val journal = JournalEntry.JournalFactory.fromPersistence(
            id = "journal-123",
            txType = JournalType.CAPTURE,
            name = "capture",

            postings = listOf(debitPosting, creditPosting)
        )

        return ledgerEntryFactory.fromPersistence(
            ledgerEntryId = 1234L,
            journalEntry = journal,
            createdAt = fixedInstant
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
            createdAt = fixedInstant.plusSeconds(2400),
            postings = listOf(debitEvent, creditEvent)
        )
    }

    @Test
    fun `toLedgerEntryEventData maps domain ledger entry to event DTO`() {
        val ledgerEntry = sampleLedgerEntry()

        val eventData = LedgerDomainEventMapper.toLedgerEntryEventData(ledgerEntry)

        Assertions.assertEquals(ledgerEntry.ledgerEntryId, eventData.ledgerEntryId)
        Assertions.assertEquals(ledgerEntry.journalEntry.id, eventData.journalEntryId)
        Assertions.assertEquals(ledgerEntry.journalEntry.txType, eventData.journalType)
        Assertions.assertEquals(Utc.toInstant(ledgerEntry.createdAt), eventData.createdAt)

        val postingEvent = eventData.postings.first()
        val postingDomain = ledgerEntry.journalEntry.postings.first()

        Assertions.assertEquals(postingDomain.account.accountCode, postingEvent.accountCode)
        Assertions.assertEquals(postingDomain.account.type, postingEvent.accountType)
        Assertions.assertEquals(postingDomain.amount.quantity, postingEvent.amount)
        Assertions.assertEquals(postingDomain.amount.currency.currencyCode, postingEvent.currency)
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

        Assertions.assertEquals("MERCHANT_PAYABLE.SELLER-333.EUR", postingDomain.account.accountCode)
        Assertions.assertEquals("SELLER-333", postingDomain.account.entityId)
        Assertions.assertEquals(AccountType.MERCHANT_PAYABLE, postingDomain.account.type)
        Assertions.assertEquals("EUR", postingDomain.account.currency.currencyCode)
        Assertions.assertTrue(postingDomain is Posting.Credit)
    }

    @Test
    fun `toDomain recreates ledger entry with postings`() {
        val eventData = sampleLedgerEntryEventData()

        val domain = LedgerDomainEventMapper.toDomain(eventData)

        assertEquals(eventData.ledgerEntryId, domain.ledgerEntryId)
        assertEquals(eventData.journalEntryId, domain.journalEntry.id)
        assertEquals(eventData.journalType, domain.journalEntry.txType)
        assertEquals(Utc.fromInstant(eventData.createdAt), domain.createdAt)

        val postings = domain.journalEntry.postings
        Assertions.assertEquals(2, postings.size)

        val debitPosting = postings.first { it is Posting.Debit }
        Assertions.assertEquals("MERCHANT_PAYABLE.SELLER-333.EUR", debitPosting.account.accountCode)
        Assertions.assertEquals(AccountType.MERCHANT_PAYABLE, debitPosting.account.type)
        Assertions.assertEquals("SELLER-333", debitPosting.account.entityId)
        Assertions.assertEquals("EUR", debitPosting.account.currency.currencyCode)
        Assertions.assertEquals(1000, debitPosting.amount.quantity)

        val creditPosting = postings.first { it is Posting.Credit }
        Assertions.assertEquals("PSP_RECEIVABLES.GLOBAL.EUR", creditPosting.account.accountCode)
        Assertions.assertEquals(AccountType.PSP_RECEIVABLES, creditPosting.account.type)
        Assertions.assertEquals("GLOBAL", creditPosting.account.entityId)
        Assertions.assertEquals("EUR", creditPosting.account.currency.currencyCode)
        Assertions.assertEquals(1000, creditPosting.amount.quantity)
    }
}