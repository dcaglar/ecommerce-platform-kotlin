package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.paymentservice.application.events.JournalEntryEventData
import com.dogancaglar.paymentservice.application.events.PostingDirection
import com.dogancaglar.paymentservice.application.events.PostingEventData
import com.dogancaglar.paymentservice.application.util.LedgerDomainEventEntityMapper.toDomain
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.domain.model.ledger.Posting
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId

class LedgerDomainEventEntityMapperTest {

    private val fixedInstant = Instant.parse("2025-11-07T16:20:00Z")

    private fun sampleJournalEntry(): JournalEntry {
        val merchantAccount = Account.Companion.mock(AccountType.MARKETPLACE_OPERATOR, "SELLER-333", "EUR")
        val pspReceivable = Account.Companion.mock(AccountType.PSP_RECEIVABLES, "GLOBAL", "EUR")
        val debitPosting = Posting.Debit.create(merchantAccount, Amount.Companion.of(1000, Currency("EUR")))
        val creditPosting = Posting.Credit.create(pspReceivable, Amount.Companion.of(1000, Currency("EUR")))

        return JournalEntry.JournalFactory.rehytrate(
            id = "journal-123",
            txType = JournalType.CAPTURE,
            name = "capture",
            paymentId = PaymentId(555L),
            txId = TxId(666L),
            postings = listOf(debitPosting, creditPosting)
        )
    }

    private fun sampleJournalEntryEventData(): JournalEntryEventData {
        val debitEvent = PostingEventData.create(
            accountCode = "MARKETPLACE_OPERATOR.SELLER-333.EUR",
            accountType = AccountType.MARKETPLACE_OPERATOR,
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

        return JournalEntryEventData.create(
            journalEntryId = "journal-9876",
            journalType = JournalType.CAPTURE,
            journalName = "capture",
            paymentId = 555L,
            txId = 666L,
            createdAt = fixedInstant.plusSeconds(2400),
            postings = listOf(debitEvent, creditEvent)
        )
    }

    @Test
    fun `toLedgerEntryEventData maps domain journal entry to event DTO`() {
        val journalEntry = sampleJournalEntry()

        val eventData = LedgerDomainEventEntityMapper.toLedgerEntryEventData(journalEntry)

        Assertions.assertEquals(journalEntry.id, eventData.journalEntryId)
        Assertions.assertEquals(journalEntry.journalType, eventData.journalType)

        val postingEvent = eventData.postings.first()
        val postingDomain = journalEntry.postings.first()

        Assertions.assertEquals(postingDomain.account.accountCode, postingEvent.accountCode)
        Assertions.assertEquals(postingDomain.account.type, postingEvent.accountType)
        Assertions.assertEquals(postingDomain.amount.quantity, postingEvent.amount)
        Assertions.assertEquals(postingDomain.amount.currency.currencyCode, postingEvent.currency)
        assertEquals(PostingDirection.DEBIT, postingEvent.direction)
    }

    @Test
    fun `PostingEventData toDomain round-trips account entity without duplicating currency`() {
        val postingEvent = PostingEventData.create(
            accountCode = "MARKETPLACE_OPERATOR.SELLER-333.EUR",
            accountType = AccountType.MARKETPLACE_OPERATOR,
            amount = 1000,
            currency = "EUR",
            direction = PostingDirection.CREDIT
        )

        val postingDomain = postingEvent.toDomain()

        Assertions.assertEquals("MARKETPLACE_OPERATOR.SELLER-333.EUR", postingDomain.account.accountCode)
        Assertions.assertEquals("SELLER-333", postingDomain.account.entityId)
        Assertions.assertEquals(AccountType.MARKETPLACE_OPERATOR, postingDomain.account.type)
        Assertions.assertEquals("EUR", postingDomain.account.currency.currencyCode)
        Assertions.assertTrue(postingDomain is Posting.Credit)
    }

    @Test
    fun `toDomain recreates journal entry with postings`() {
        val eventData = sampleJournalEntryEventData()

        val domain = LedgerDomainEventEntityMapper.toDomain(eventData)

        assertEquals(eventData.journalEntryId, domain.id)
        assertEquals(eventData.journalType, domain.journalType)

        val postings = domain.postings
        Assertions.assertEquals(2, postings.size)

        val debitPosting = postings.first { it is Posting.Debit }
        Assertions.assertEquals("MARKETPLACE_OPERATOR.SELLER-333.EUR", debitPosting.account.accountCode)
        Assertions.assertEquals(AccountType.MARKETPLACE_OPERATOR, debitPosting.account.type)
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