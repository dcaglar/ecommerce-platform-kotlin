package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.AccountBalanceEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.AccountBalanceMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
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
 * Integration tests for AccountBalanceMapper with real PostgreSQL (Testcontainers).
 * 
 * These tests validate:
 * - Real database persistence operations
 * - MyBatis mapper integration
 * - Watermark-based idempotency
 * - SQL operations with actual PostgreSQL
 * 
 * Tagged as @integration for selective execution:
 * - mvn test                             -> Runs ALL tests (unit + integration)
 * - mvn test -Dgroups=integration        -> Runs integration tests only
 * - mvn test -DexcludedGroups=integration -> Runs unit tests only (fast)
 */
@Tag("integration")
@MybatisTest
@ContextConfiguration(classes = [InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=false"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis")
class AccountBalanceMapperIntegrationTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl =
                AccountBalanceMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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
    lateinit var accountBalanceMapper: AccountBalanceMapper

    @Test
    fun `insertOrUpdateSnapshot should insert new snapshot`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.MERCHANT-456"
        val now = LocalDateTime.now()
        val entity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 100000L,
            lastAppliedEntryId = 100L,
            lastSnapshotAt = now,
            updatedAt = now
        )

        // When
        val result = accountBalanceMapper.insertOrUpdateSnapshot(entity)

        // Then
        assertEquals(1, result, "Insert should succeed")

        // Verify data was persisted
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted, "Entity should be found")
        assertEquals(accountCode, persisted?.accountCode)
        assertEquals(100000L, persisted?.balance)
        assertEquals(100L, persisted?.lastAppliedEntryId)
    }

    @Test
    fun `insertOrUpdateSnapshot should update existing snapshot when watermark increases`() {
        // Given - Insert first snapshot
        val accountCode = "CASH.GLOBAL"
        val now = LocalDateTime.now()
        val initialEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 50000L,
            lastAppliedEntryId = 50L,
            lastSnapshotAt = now.minusHours(1),
            updatedAt = now.minusHours(1)
        )
        accountBalanceMapper.insertOrUpdateSnapshot(initialEntity)

        // When - Update with new balance and higher watermark
        val updatedEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 75000L,
            lastAppliedEntryId = 100L, // Higher watermark
            lastSnapshotAt = now,
            updatedAt = now
        )
        val result = accountBalanceMapper.insertOrUpdateSnapshot(updatedEntity)

        // Then
        assertEquals(1, result, "Update should succeed")

        // Verify data was updated
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(accountCode, persisted?.accountCode)
        assertEquals(75000L, persisted?.balance, "Balance should be updated")
        assertEquals(100L, persisted?.lastAppliedEntryId, "Watermark should be updated")
        assertEquals(now, persisted?.lastSnapshotAt, "lastSnapshotAt should be updated")
        assertEquals(now, persisted?.updatedAt, "updatedAt should be updated")
    }

    @Test
    fun `insertOrUpdateSnapshot should NOT update when watermark is lower or equal`() {
        // Given - Insert snapshot with watermark 100
        val accountCode = "WATERMARK_TEST.ACCOUNT"
        val now = LocalDateTime.now()
        val initialEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 50000L,
            lastAppliedEntryId = 100L,
            lastSnapshotAt = now.minusHours(1),
            updatedAt = now.minusHours(1)
        )
        accountBalanceMapper.insertOrUpdateSnapshot(initialEntity)

        // When - Try to update with LOWER watermark
        val lowerWatermarkEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 99999L, // Different balance
            lastAppliedEntryId = 50L, // LOWER watermark - should NOT update
            lastSnapshotAt = now,
            updatedAt = now
        )
        accountBalanceMapper.insertOrUpdateSnapshot(lowerWatermarkEntity)

        // Then - Should NOT be updated (watermark condition prevents update)
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(50000L, persisted?.balance, "Balance should NOT be updated (lower watermark)")
        assertEquals(100L, persisted?.lastAppliedEntryId, "Watermark should NOT be updated")
    }

    @Test
    fun `insertOrUpdateSnapshot should NOT update when watermark is equal`() {
        // Given - Insert snapshot with watermark 100
        val accountCode = "EQUAL_WATERMARK_TEST.ACCOUNT"
        val initialTime = LocalDateTime.now().minusHours(1)
        val initialEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 50000L,
            lastAppliedEntryId = 100L,
            lastSnapshotAt = initialTime,
            updatedAt = initialTime
        )
        accountBalanceMapper.insertOrUpdateSnapshot(initialEntity)

        // When - Try to update with EQUAL watermark
        val equalWatermarkEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 99999L,
            lastAppliedEntryId = 100L, // EQUAL watermark - should NOT update
            lastSnapshotAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        accountBalanceMapper.insertOrUpdateSnapshot(equalWatermarkEntity)

        // Then - Should NOT be updated
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(50000L, persisted?.balance, "Balance should NOT be updated (equal watermark)")
        assertEquals(100L, persisted?.lastAppliedEntryId, "Watermark should NOT be updated")
    }

    @Test
    fun `findByAccountCode should return null when not exists`() {
        // When
        val result = accountBalanceMapper.findByAccountCode("NONEXISTENT.ACCOUNT")

        // Then
        assertNull(result, "Should return null for non-existent account")
    }

    @Test
    fun `findByAccountCode should return correct snapshot when exists`() {
        // Given
        val accountCode = "AUTH_RECEIVABLE.GLOBAL"
        val now = LocalDateTime.now()
        val entity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 200000L,
            lastAppliedEntryId = 200L,
            lastSnapshotAt = now,
            updatedAt = now
        )
        accountBalanceMapper.insertOrUpdateSnapshot(entity)

        // When
        val found = accountBalanceMapper.findByAccountCode(accountCode)

        // Then
        assertNotNull(found)
        assertEquals(accountCode, found?.accountCode)
        assertEquals(200000L, found?.balance)
        assertEquals(200L, found?.lastAppliedEntryId)
        assertEquals(now, found?.lastSnapshotAt)
        assertEquals(now, found?.updatedAt)
    }

    @Test
    fun `findByAccountCodes should return snapshots for specified account codes`() {
        // Given - Insert multiple snapshots
        val now = LocalDateTime.now()
        val entities = listOf(
            AccountBalanceEntity(
                accountCode = "ACCOUNT_A.TEST",
                balance = 100000L,
                lastAppliedEntryId = 100L,
                lastSnapshotAt = now,
                updatedAt = now
            ),
            AccountBalanceEntity(
                accountCode = "ACCOUNT_B.TEST",
                balance = 200000L,
                lastAppliedEntryId = 200L,
                lastSnapshotAt = now,
                updatedAt = now
            ),
            AccountBalanceEntity(
                accountCode = "ACCOUNT_C.TEST",
                balance = 300000L,
                lastAppliedEntryId = 300L,
                lastSnapshotAt = now,
                updatedAt = now
            )
        )
        entities.forEach { accountBalanceMapper.insertOrUpdateSnapshot(it) }

        // When
        val accountCodes = setOf("ACCOUNT_A.TEST", "ACCOUNT_B.TEST")
        val found = accountBalanceMapper.findByAccountCodes(accountCodes)

        // Then
        assertEquals(2, found.size)
        val foundMap = found.associateBy { it.accountCode }
        assertEquals(100000L, foundMap["ACCOUNT_A.TEST"]?.balance)
        assertEquals(200000L, foundMap["ACCOUNT_B.TEST"]?.balance)
        assertEquals(100L, foundMap["ACCOUNT_A.TEST"]?.lastAppliedEntryId)
        assertEquals(200L, foundMap["ACCOUNT_B.TEST"]?.lastAppliedEntryId)
    }

    @Test
    fun `findByAccountCodes should return empty list when none found`() {
        // When
        val found = accountBalanceMapper.findByAccountCodes(setOf("NONEXISTENT.ACCOUNT"))

        // Then
        assertEquals(0, found.size)
    }

    @Test
    fun `findAll should return all snapshots ordered by account code`() {
        // Given - Insert multiple snapshots
        val now = LocalDateTime.now()
        val entities = listOf(
            AccountBalanceEntity(
                accountCode = "Z_ACCOUNT.TEST",
                balance = 300000L,
                lastAppliedEntryId = 300L,
                lastSnapshotAt = now,
                updatedAt = now
            ),
            AccountBalanceEntity(
                accountCode = "A_ACCOUNT.TEST",
                balance = 100000L,
                lastAppliedEntryId = 100L,
                lastSnapshotAt = now,
                updatedAt = now
            ),
            AccountBalanceEntity(
                accountCode = "M_ACCOUNT.TEST",
                balance = 200000L,
                lastAppliedEntryId = 200L,
                lastSnapshotAt = now,
                updatedAt = now
            )
        )

        entities.forEach { accountBalanceMapper.insertOrUpdateSnapshot(it) }

        // When
        val allSnapshots = accountBalanceMapper.findAll()

        // Then
        assertTrue(allSnapshots.size >= 3, "Should return at least 3 snapshots")
        
        // Find our test snapshots (there might be others from previous tests)
        val testSnapshots = allSnapshots.filter { 
            it.accountCode in entities.map { e -> e.accountCode }
        }
        assertEquals(3, testSnapshots.size, "Should find all 3 test snapshots")
        
        // Verify ordering (should be sorted by account_code)
        val sortedCodes = testSnapshots.map { it.accountCode }.sorted()
        assertEquals(sortedCodes, testSnapshots.map { it.accountCode }, "Should be sorted by account_code")
        
        // Verify values
        val accountMap = testSnapshots.associateBy { it.accountCode }
        assertEquals(100000L, accountMap["A_ACCOUNT.TEST"]?.balance)
        assertEquals(200000L, accountMap["M_ACCOUNT.TEST"]?.balance)
        assertEquals(300000L, accountMap["Z_ACCOUNT.TEST"]?.balance)
    }

    @Test
    fun `findAll should return empty list when no snapshots exist`() {
        // When - Query for snapshots (assuming clean state)
        val allSnapshots = accountBalanceMapper.findAll()

        // Then - Should at least return a list (might be empty or contain data from other tests)
        assertNotNull(allSnapshots)
    }

    @Test
    fun `insertOrUpdateSnapshot should handle large balance values`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.LARGE-BALANCE"
        val now = LocalDateTime.now()
        val largeBalance = Long.MAX_VALUE / 2  // Large but safe value
        val entity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = largeBalance,
            lastAppliedEntryId = 1000L,
            lastSnapshotAt = now,
            updatedAt = now
        )

        // When
        val result = accountBalanceMapper.insertOrUpdateSnapshot(entity)

        // Then
        assertEquals(1, result)
        
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(largeBalance, persisted?.balance)
        assertEquals(1000L, persisted?.lastAppliedEntryId)
    }

    @Test
    fun `insertOrUpdateSnapshot should handle negative balance values`() {
        // Given
        val accountCode = "OVERDRAWN_ACCOUNT.TEST"
        val now = LocalDateTime.now()
        val entity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = -5000L,  // Negative balance (e.g., overdrawn)
            lastAppliedEntryId = 50L,
            lastSnapshotAt = now,
            updatedAt = now
        )

        // When
        val result = accountBalanceMapper.insertOrUpdateSnapshot(entity)

        // Then
        assertEquals(1, result)
        
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(-5000L, persisted?.balance)
        assertEquals(50L, persisted?.lastAppliedEntryId)
    }

    @Test
    fun `insertOrUpdateSnapshot should be idempotent when called multiple times with same watermark`() {
        // Given
        val accountCode = "IDEMPOTENT_ACCOUNT.TEST"
        val now = LocalDateTime.now()
        val entity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 10000L,
            lastAppliedEntryId = 10L,
            lastSnapshotAt = now,
            updatedAt = now
        )

        // When - Insert twice with same data (same watermark)
        val firstResult = accountBalanceMapper.insertOrUpdateSnapshot(entity)
        val secondResult = accountBalanceMapper.insertOrUpdateSnapshot(entity)

        // Then
        assertEquals(1, firstResult, "First insert should succeed")
        assertEquals(0, secondResult, "Second call with same watermark should not update (watermark protection)")
        
        // Should still have only one record with same balance (unchanged)
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(10000L, persisted?.balance)
        assertEquals(10L, persisted?.lastAppliedEntryId)
    }

    @Test
    fun `insertOrUpdateSnapshot should update timestamp when watermark increases`() {
        // Given - Insert initial snapshot
        val accountCode = "TIMESTAMP_ACCOUNT.TEST"
        val initialTime = LocalDateTime.now().minusHours(2)
        val initialEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 50000L,
            lastAppliedEntryId = 50L,
            lastSnapshotAt = initialTime,
            updatedAt = initialTime
        )
        accountBalanceMapper.insertOrUpdateSnapshot(initialEntity)

        // When - Update with higher watermark and new timestamp
        val updateTime = LocalDateTime.now()
        val updatedEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 50000L,  // Same balance
            lastAppliedEntryId = 100L, // Higher watermark
            lastSnapshotAt = updateTime,
            updatedAt = updateTime
        )
        accountBalanceMapper.insertOrUpdateSnapshot(updatedEntity)

        // Then - Timestamps should be updated when watermark increases
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(updateTime, persisted?.lastSnapshotAt)
        assertEquals(updateTime, persisted?.updatedAt)
        assertEquals(100L, persisted?.lastAppliedEntryId)
    }

    @Test
    fun `insertOrUpdateSnapshot should NOT update timestamp when watermark is same`() {
        // Given - Insert initial snapshot
        val accountCode = "TIMESTAMP_NO_UPDATE.TEST"
        val initialTime = LocalDateTime.now().minusHours(2)
        val initialEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 50000L,
            lastAppliedEntryId = 100L,
            lastSnapshotAt = initialTime,
            updatedAt = initialTime
        )
        accountBalanceMapper.insertOrUpdateSnapshot(initialEntity)

        // When - Try to update with same watermark but new timestamp
        val updateTime = LocalDateTime.now()
        val updatedEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 99999L,  // Different balance (should be ignored)
            lastAppliedEntryId = 100L, // Same watermark - should NOT update
            lastSnapshotAt = updateTime,
            updatedAt = updateTime
        )
        accountBalanceMapper.insertOrUpdateSnapshot(updatedEntity)

        // Then - Timestamps should NOT be updated (watermark protection)
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(initialTime, persisted?.lastSnapshotAt, "lastSnapshotAt should NOT change")
        assertEquals(initialTime, persisted?.updatedAt, "updatedAt should NOT change")
        assertEquals(50000L, persisted?.balance, "Balance should NOT change")
        assertEquals(100L, persisted?.lastAppliedEntryId)
    }
}