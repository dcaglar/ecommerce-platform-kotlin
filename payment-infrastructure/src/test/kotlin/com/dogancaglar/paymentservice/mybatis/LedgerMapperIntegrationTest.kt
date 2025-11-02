package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.JournalEntryEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.LedgerEntryEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PostingEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mybatis.spring.annotation.MapperScan
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

/**
 * Integration tests for LedgerMapper - tests MyBatis mapping to journal_entries and postings tables.
 * 
 * LedgerMapper is a simple interface with two insert operations:
 * - insertJournalEntry() - inserts into journal_entries table
 * - insertPosting() - inserts into postings table
 */
@MybatisTest
@ContextConfiguration(classes = [InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=false"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis")
class LedgerMapperIntegrationTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl =
                LedgerMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
            postgres.createConnection("").use { c -> c.createStatement().execute(ddl) }
        }

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }

        @DynamicPropertySource
        @JvmStatic
        fun datasourceProps(reg: DynamicPropertyRegistry) {
            reg.add("spring.datasource.url", postgres::getJdbcUrl)
            reg.add("spring.datasource.username", postgres::getUsername)
            reg.add("spring.datasource.password", postgres::getPassword)
            reg.add("spring.datasource.driver-class-name", postgres::getDriverClassName)
        }
    }

    @Autowired
    lateinit var ledgerMapper: LedgerMapper

    // ==================== insertJournalEntry Tests ====================

    @Test
    fun `insertJournalEntry should insert a new journal entry`() {
        val entry = JournalEntryEntity(
            id = "journal-123",
            txType = "AUTH_HOLD",
            name = "Test Entry",
            referenceType = "payment_order",
            referenceId = "order-456",
            createdAt = LocalDateTime.now()
        )

        val result = ledgerMapper.insertJournalEntry(entry)

        assertEquals(1, result)
        
        // Verify data was persisted
        val persisted = ledgerMapper.findByJournalId("journal-123")
        assertNotNull(persisted)
        assertEquals("journal-123", persisted?.id)
        assertEquals("AUTH_HOLD", persisted?.txType)
        assertEquals("Test Entry", persisted?.name)
        assertEquals("payment_order", persisted?.referenceType)
        assertEquals("order-456", persisted?.referenceId)
    }

    @Test
    fun `insertJournalEntry should ignore duplicate inserts (ON CONFLICT)`() {
        val entry = JournalEntryEntity(
            id = "journal-dup",
            txType = "CAPTURE",
            name = "Duplicate Test",
            referenceType = null,
            referenceId = null,
            createdAt = LocalDateTime.now()
        )

        val firstInsert = ledgerMapper.insertJournalEntry(entry)
        val duplicateInsert = ledgerMapper.insertJournalEntry(entry) // Same ID

        assertEquals(1, firstInsert)
        assertEquals(0, duplicateInsert, "Duplicate should be ignored due to ON CONFLICT")
        
        // Verify only one entry exists
        val persisted = ledgerMapper.findByJournalId("journal-dup")
        assertNotNull(persisted)
        assertEquals("CAPTURE", persisted?.txType)
    }

    @Test
    fun `insertJournalEntry should handle null optional fields`() {
        val entry = JournalEntryEntity(
            id = "journal-nulls",
            txType = "REFUND",
            name = "Test with nulls",
            referenceType = null,
            referenceId = null,
            createdAt = LocalDateTime.now()
        )

        val result = ledgerMapper.insertJournalEntry(entry)

        assertEquals(1, result)
        
        // Verify data was persisted with nulls
        val persisted = ledgerMapper.findByJournalId("journal-nulls")
        assertNotNull(persisted)
        assertEquals("REFUND", persisted?.txType)
        assertNull(persisted?.referenceType)
        assertNull(persisted?.referenceId)
    }

    // ==================== insertLedgerEntry Tests ====================

    @Test
    fun `insertLedgerEntry should insert a new ledger entry and return generated ID`() {
        // First create the journal entry
        val journalId = "journal-for-ledger"
        ledgerMapper.insertJournalEntry(JournalEntryEntity(
            id = journalId,
            txType = "AUTH_HOLD",
            name = "Test",
            referenceType = null,
            referenceId = null,
            createdAt = LocalDateTime.now()
        ))

        val ledgerEntry = LedgerEntryEntity(
            id = null, // Will be auto-generated
            journalId = journalId,
            createdAt = LocalDateTime.now()
        )

        val result = ledgerMapper.insertLedgerEntry(ledgerEntry)

        assertEquals(1, result)
        assertNotNull(ledgerEntry.id, "ID should be populated by MyBatis after insert")
        assertTrue(ledgerEntry.id!! > 0, "Generated ID should be positive")
    }

    @Test
    fun `insertLedgerEntry should generate unique IDs for multiple entries`() {
        val journalId = "journal-multi-ledger"
        ledgerMapper.insertJournalEntry(JournalEntryEntity(
            id = journalId,
            txType = "CAPTURE",
            name = "Multi Ledger Test",
            referenceType = null,
            referenceId = null,
            createdAt = LocalDateTime.now()
        ))

        val ledgerEntry1 = LedgerEntryEntity(
            id = null,
            journalId = journalId,
            createdAt = LocalDateTime.now()
        )

        val ledgerEntry2 = LedgerEntryEntity(
            id = null,
            journalId = journalId,
            createdAt = LocalDateTime.now()
        )

        val result1 = ledgerMapper.insertLedgerEntry(ledgerEntry1)
        val result2 = ledgerMapper.insertLedgerEntry(ledgerEntry2)

        assertEquals(1, result1)
        assertEquals(1, result2)
        assertNotNull(ledgerEntry1.id)
        assertNotNull(ledgerEntry2.id)
        assertNotEquals(ledgerEntry1.id, ledgerEntry2.id, "Each entry should have unique ID")
    }

    // ==================== insertPosting Tests ====================

    @Test
    fun `insertPosting should insert a new posting`() {
        // First create the journal entry
        val journalId = "journal-for-posting"
        ledgerMapper.insertJournalEntry(JournalEntryEntity(
            id = journalId,
            txType = "CAPTURE",
            name = "Test",
            referenceType = null,
            referenceId = null,
            createdAt = LocalDateTime.now()
        ))

        val posting = PostingEntity(
            id = null,
            journalId = journalId,
            accountCode = "MERCHANT-seller-123",
            accountType = "MERCHANT_ACCOUNT",
            amount = 10000L,
            direction = "DEBIT",
            currency = "USD",
            createdAt = LocalDateTime.now()
        )

        val result = ledgerMapper.insertPosting(posting)

        assertEquals(1, result)
        
        // Verify posting was persisted
        val persisted = ledgerMapper.findPostingsByJournalId(journalId)
        assertEquals(1, persisted.size)
        assertEquals("MERCHANT-seller-123", persisted[0].accountCode)
        assertEquals("MERCHANT_ACCOUNT", persisted[0].accountType)
        assertEquals(10000L, persisted[0].amount)
        assertEquals("DEBIT", persisted[0].direction)
        assertEquals("USD", persisted[0].currency)
    }

    @Test
    fun `insertPosting should allow multiple postings per journal`() {
        val journalId = "journal-multi"
        ledgerMapper.insertJournalEntry(JournalEntryEntity(
            id = journalId,
            txType = "CAPTURE",
            name = "Multi Posting Test",
            referenceType = null,
            referenceId = null,
            createdAt = LocalDateTime.now()
        ))

        val posting1 = PostingEntity(
            id = null,
            journalId = journalId,
            accountCode = "ACCOUNT-1",
            accountType = "MERCHANT_ACCOUNT",
            amount = 10000L,
            direction = "DEBIT",
            currency = "USD",
            createdAt = LocalDateTime.now()
        )

        val posting2 = PostingEntity(
            id = null,
            journalId = journalId,
            accountCode = "ACCOUNT-2",
            accountType = "ACQUIRER_ACCOUNT",
            amount = 10000L,
            direction = "CREDIT",
            currency = "USD",
            createdAt = LocalDateTime.now()
        )

        assertEquals(1, ledgerMapper.insertPosting(posting1))
        assertEquals(1, ledgerMapper.insertPosting(posting2))
        
        // Verify both postings were persisted
        val persisted = ledgerMapper.findPostingsByJournalId(journalId)
        assertEquals(2, persisted.size)
        assertTrue(persisted.any { it.accountCode == "ACCOUNT-1" })
        assertTrue(persisted.any { it.accountCode == "ACCOUNT-2" })
    }

    @Test
    fun `insertPosting should ignore duplicate inserts for same journal and account`() {
        val journalId = "journal-dup-posting"
        ledgerMapper.insertJournalEntry(JournalEntryEntity(
            id = journalId,
            txType = "AUTH_HOLD",
            name = "Test",
            referenceType = null,
            referenceId = null,
            createdAt = LocalDateTime.now()
        ))

        val posting = PostingEntity(
            id = null,
            journalId = journalId,
            accountCode = "SAME-ACCOUNT",
            accountType = "MERCHANT_ACCOUNT",
            amount = 5000L,
            direction = "DEBIT",
            currency = "EUR",
            createdAt = LocalDateTime.now()
        )

        val firstInsert = ledgerMapper.insertPosting(posting)
        val duplicateInsert = ledgerMapper.insertPosting(posting) // Same journal_id + account_code

        assertEquals(1, firstInsert)
        assertEquals(0, duplicateInsert, "Duplicate should be ignored due to unique constraint")
        
        // Verify only one posting exists
        val persisted = ledgerMapper.findPostingsByJournalId(journalId)
        assertEquals(1, persisted.size)
        assertEquals("SAME-ACCOUNT", persisted[0].accountCode)
    }
}
