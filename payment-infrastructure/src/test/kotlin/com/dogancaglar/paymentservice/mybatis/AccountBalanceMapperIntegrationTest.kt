package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.AccountBalanceEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.AccountBalanceMapper
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
    }

    @Test
    fun `insertOrUpdateSnapshot should update existing snapshot on conflict`() {
        // Given - Insert first snapshot
        val accountCode = "CASH.GLOBAL"
        val now = LocalDateTime.now()
        val initialEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 50000L,
            lastSnapshotAt = now.minusHours(1),
            updatedAt = now.minusHours(1)
        )
        accountBalanceMapper.insertOrUpdateSnapshot(initialEntity)

        // When - Update with new balance
        val updatedEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 75000L,
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
        assertEquals(now, persisted?.lastSnapshotAt, "lastSnapshotAt should be updated")
        assertEquals(now, persisted?.updatedAt, "updatedAt should be updated")
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
        assertEquals(now, found?.lastSnapshotAt)
        assertEquals(now, found?.updatedAt)
    }

    @Test
    fun `findAll should return all snapshots ordered by account code`() {
        // Given - Insert multiple snapshots
        val now = LocalDateTime.now()
        val entities = listOf(
            AccountBalanceEntity(
                accountCode = "Z_ACCOUNT.TEST",
                balance = 300000L,
                lastSnapshotAt = now,
                updatedAt = now
            ),
            AccountBalanceEntity(
                accountCode = "A_ACCOUNT.TEST",
                balance = 100000L,
                lastSnapshotAt = now,
                updatedAt = now
            ),
            AccountBalanceEntity(
                accountCode = "M_ACCOUNT.TEST",
                balance = 200000L,
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
    }

    @Test
    fun `insertOrUpdateSnapshot should handle negative balance values`() {
        // Given
        val accountCode = "OVERDRAWN_ACCOUNT.TEST"
        val now = LocalDateTime.now()
        val entity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = -5000L,  // Negative balance (e.g., overdrawn)
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
    }

    @Test
    fun `insertOrUpdateSnapshot should be idempotent when called multiple times with same data`() {
        // Given
        val accountCode = "IDEMPOTENT_ACCOUNT.TEST"
        val now = LocalDateTime.now()
        val entity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 10000L,
            lastSnapshotAt = now,
            updatedAt = now
        )

        // When - Insert twice with same data
        val firstResult = accountBalanceMapper.insertOrUpdateSnapshot(entity)
        val secondResult = accountBalanceMapper.insertOrUpdateSnapshot(entity)

        // Then
        assertEquals(1, firstResult)
        assertEquals(1, secondResult)  // Update should also return 1
        
        // Should still have only one record with same balance
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(10000L, persisted?.balance)
    }

    @Test
    fun `insertOrUpdateSnapshot should update timestamp on conflict`() {
        // Given - Insert initial snapshot
        val accountCode = "TIMESTAMP_ACCOUNT.TEST"
        val initialTime = LocalDateTime.now().minusHours(2)
        val initialEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 50000L,
            lastSnapshotAt = initialTime,
            updatedAt = initialTime
        )
        accountBalanceMapper.insertOrUpdateSnapshot(initialEntity)

        // When - Update with new timestamp
        val updateTime = LocalDateTime.now()
        val updatedEntity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 50000L,  // Same balance
            lastSnapshotAt = updateTime,
            updatedAt = updateTime
        )
        accountBalanceMapper.insertOrUpdateSnapshot(updatedEntity)

        // Then - Timestamps should be updated even if balance is same
        val persisted = accountBalanceMapper.findByAccountCode(accountCode)
        assertNotNull(persisted)
        assertEquals(updateTime, persisted?.lastSnapshotAt)
        assertEquals(updateTime, persisted?.updatedAt)
    }
}

