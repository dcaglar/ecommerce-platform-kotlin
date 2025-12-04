package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.ports.outbound.IdempotencyRecord
import com.dogancaglar.paymentservice.ports.outbound.IdempotencyStatus
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.IdempotencyKeyMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.serialization.JacksonUtil
import org.junit.jupiter.api.BeforeEach
import java.time.Instant

/**
 * Integration tests for IdempotencyKeyMapper with real PostgreSQL (Testcontainers).
 * 
 * These tests validate:
 * - Real database persistence operations for idempotency keys
 * - MyBatis mapper integration
 * - Idempotency key CRUD operations
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
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis")
class IdempotencyKeyMapperIntegrationTest {

    /**
     * Normalizes Instant to microsecond precision to match PostgreSQL's TIMESTAMP precision.
     * PostgreSQL stores timestamps with microsecond precision (6 decimal places),
     * but Java Instant can have nanosecond precision (9 decimal places).
     */
    private fun Instant.normalizeToMicroseconds(): Instant {
        return this.truncatedTo(java.time.temporal.ChronoUnit.MICROS)
    }

    /**
     * Normalizes IdempotencyRecord timestamps to microsecond precision for comparison.
     */
    private fun IdempotencyRecord.normalizeTimestamps(): IdempotencyRecord {
        return this.copy(
            createdAt = this.createdAt.normalizeToMicroseconds()
        )
    }

    @BeforeEach
    fun cleanDB() {
        postgres.createConnection("").use { c ->
            val s = c.createStatement()
            s.execute("TRUNCATE TABLE idempotency_keys CASCADE;")
            s.execute("TRUNCATE TABLE payments CASCADE;")
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl =
                IdempotencyKeyMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
            postgres.createConnection("").use { c -> c.createStatement().execute(ddl) }
        }

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
    lateinit var idempotencyKeyMapper: IdempotencyKeyMapper

    @Autowired
    lateinit var paymentMapper: PaymentMapper

    private val objectMapper = JacksonUtil.createObjectMapper()

    private fun assertJsonEquals(expectedJson: String, actualJson: String?) {
        assertNotNull(actualJson, "JSON should not be null")
        val expectedTree = objectMapper.readTree(expectedJson)
        val actualTree = objectMapper.readTree(actualJson)
        assertEquals(expectedTree, actualTree, "JSON content should match (order may differ)")
    }

    private fun createPayment(paymentId: Long) {
        val now = Utc.nowInstant().normalizeToMicroseconds()
        val paymentLines = listOf(
            PaymentLine(
                sellerId = SellerId("seller-1"),
                amount = Amount.of(10_000, Currency("EUR"))
            )
        )
        paymentMapper.insert(
            PaymentEntity(
                paymentId = paymentId,
                buyerId = "buyer-$paymentId",
                orderId = "order-$paymentId",
                totalAmountValue = 10_000,
                capturedAmountValue = 0,
                currency = "EUR",
                status = "PENDING_AUTH",
                createdAt = now,
                updatedAt = now,
                paymentLinesJson = objectMapper.writeValueAsString(paymentLines)
            )
        )
    }

    private fun sampleRecord(
        key: String = "test-key-1",
        requestHash: String = "hash-abc123",
        paymentId: Long? = null,
        responsePayload: String? = null,
        status: IdempotencyStatus = IdempotencyStatus.PENDING,
        createdAt: Instant = Utc.nowInstant().normalizeToMicroseconds()
    ): IdempotencyRecord {
        return IdempotencyRecord(
            idempotencyKey = key,
            requestHash = requestHash,
            paymentId = paymentId,
            responsePayload = responsePayload,
            status = status,
            createdAt = createdAt
        )
    }



    @Test
    fun `insertPending first time inserts row and second time returns 0`() {
        val key = "idem-test-1"
        val hash1 = "hash-abc"

        val nowBefore = Utc.nowInstant().normalizeToMicroseconds()

        // first insert
        val first = idempotencyKeyMapper.insertPending(
            sampleRecord(
                key = key,
                requestHash = hash1
            )
        )
        assertEquals(key, first)

        val nowAfter = Utc.nowInstant().normalizeToMicroseconds()

        val row1 = idempotencyKeyMapper.findByKey(key)
        assertNotNull(row1)
        assertEquals(key, row1!!.idempotencyKey)
        assertEquals(hash1, row1.requestHash)
        assertEquals(IdempotencyStatus.PENDING, row1.status)
        assertNull(row1.paymentId)
        assertNull(row1.responsePayload)
        // createdAt should be between nowBefore and nowAfter (with tolerance for clock differences)
        // Database generates timestamp, so allow 2 seconds tolerance for clock skew
        val createdAt = row1.createdAt.normalizeToMicroseconds()
        assertTrue(createdAt >= nowBefore.minusSeconds(2), 
            "createdAt ($createdAt) should be >= nowBefore - 2s ($nowBefore)")
        assertTrue(createdAt <= nowAfter.plusSeconds(2),
            "createdAt ($createdAt) should be <= nowAfter + 2s ($nowAfter)")

        // duplicate insert with DIFFERENT hash must be ignored by ON CONFLICT
        val hash2 = "hash-xyz"
        val second = idempotencyKeyMapper.insertPending(
            sampleRecord(
                key = key,
                requestHash = hash2
            )
        )
        assertNull(second)

        val row2 = idempotencyKeyMapper.findByKey(key)
        assertNotNull(row2)
        // hash MUST stay as first one, because second insert was ignored
        assertEquals(hash1, row2!!.requestHash)
    }

    @Test
    fun `updatePaymentId sets paymentId when row exists`() {
        val key = "idem-test-2"
        val hash = "hash-123"

        // create a real payment row (FK safety if schema uses FK)
        val paymentId = 1001L
        createPayment(paymentId)

        idempotencyKeyMapper.insertPending(
            sampleRecord(
                key = key,
                requestHash = hash
            )
        )

        val updatedRows = idempotencyKeyMapper.updatePaymentId(key, paymentId)
        assertEquals(1, updatedRows)

        val row = idempotencyKeyMapper.findByKey(key)
        assertNotNull(row)
        assertEquals(paymentId, row!!.paymentId)
    }

    @Test
    fun `updateResponsePayload writes jsonb and marks COMPLETED`() {
        val key = "idem-test-3"
        val hash = "hash-456"

        idempotencyKeyMapper.insertPending(
            sampleRecord(
                key = key,
                requestHash = hash
            )
        )

        val payload = """{"paymentId":"p-123","status":"AUTHORIZED"}"""
        val updatedRows = idempotencyKeyMapper.updateResponsePayload(key, payload)
        assertEquals(1, updatedRows)

        val row = idempotencyKeyMapper.findByKey(key)
        assertNotNull(row)
        assertEquals(IdempotencyStatus.COMPLETED, row!!.status)
        assertJsonEquals(payload, row.responsePayload)
    }

    @Test
    fun `deletePending deletes only PENDING rows without payload`() {
        val keyPending = "idem-test-4-pending"
        val keyCompleted = "idem-test-4-completed"

        // PENDING, no payload
        idempotencyKeyMapper.insertPending(
            sampleRecord(
                key = keyPending,
                requestHash = "hash-pending"
            )
        )

        // PENDING → COMPLETED with payload
        idempotencyKeyMapper.insertPending(
            sampleRecord(
                key = keyCompleted,
                requestHash = "hash-completed"
            )
        )
        idempotencyKeyMapper.updateResponsePayload(
            keyCompleted,
            """{"ok":true}"""
        )

        // when
        val deletedPending = idempotencyKeyMapper.deletePending(keyPending)
        val deletedCompleted = idempotencyKeyMapper.deletePending(keyCompleted)

        // then
        assertEquals(1, deletedPending)   // pending row removed
        assertEquals(0, deletedCompleted) // completed row kept

        val rowPending = idempotencyKeyMapper.findByKey(keyPending)
        val rowCompleted = idempotencyKeyMapper.findByKey(keyCompleted)

        assertNull(rowPending)
        assertNotNull(rowCompleted)
        assertEquals(IdempotencyStatus.COMPLETED, rowCompleted!!.status)
    }
}

