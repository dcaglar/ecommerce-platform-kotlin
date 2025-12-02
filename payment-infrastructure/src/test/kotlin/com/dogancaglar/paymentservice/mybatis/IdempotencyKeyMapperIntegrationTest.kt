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

    private val objectMapper = ObjectMapper()

    private fun assertJsonEquals(expectedJson: String, actualJson: String?) {
        assertNotNull(actualJson, "JSON should not be null")
        val expectedTree = objectMapper.readTree(expectedJson)
        val actualTree = objectMapper.readTree(actualJson)
        assertEquals(expectedTree, actualTree, "JSON content should match (order may differ)")
    }

    private fun createPayment(paymentId: Long) {
        val now = Utc.nowInstant().normalizeToMicroseconds()
        paymentMapper.insertIgnore(
            PaymentEntity(
                paymentId = paymentId,
                buyerId = "buyer-$paymentId",
                orderId = "order-$paymentId",
                totalAmountValue = 10_000,
                capturedAmountValue = 0,
                currency = "EUR",
                status = "PENDING_AUTH",
                idempotencyKey = "payment-key-$paymentId",
                createdAt = now,
                updatedAt = now
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
    fun `insertPending should insert new idempotency key record`() {
        val record = sampleRecord(
            key = "key-001",
            requestHash = "hash-001"
        )

        val inserted = idempotencyKeyMapper.insertPending(record)
        assertEquals(1, inserted, "Should insert one row")

        val found = idempotencyKeyMapper.findByKey("key-001")
        assertNotNull(found)
        assertEquals("key-001", found!!.idempotencyKey)
        assertEquals("hash-001", found.requestHash)
        assertEquals(IdempotencyStatus.PENDING, found.status)
        assertNull(found.paymentId)
        assertNull(found.responsePayload)
    }

    @Test
    fun `insertPending should ignore duplicate idempotency keys`() {
        val record1 = sampleRecord(
            key = "duplicate-key",
            requestHash = "hash-first"
        )
        val record2 = sampleRecord(
            key = "duplicate-key",
            requestHash = "hash-second"
        )

        val firstInsert = idempotencyKeyMapper.insertPending(record1)
        assertEquals(1, firstInsert, "First insert should succeed")

        val secondInsert = idempotencyKeyMapper.insertPending(record2)
        assertEquals(0, secondInsert, "ON CONFLICT DO NOTHING should skip duplicate")

        val found = idempotencyKeyMapper.findByKey("duplicate-key")
        assertNotNull(found)
        assertEquals("hash-first", found!!.requestHash, "Should keep original request hash")
        assertEquals(IdempotencyStatus.PENDING, found.status)
    }

    @Test
    fun `findByKey should return null for non-existent key`() {
        val found = idempotencyKeyMapper.findByKey("non-existent-key")
        assertNull(found)
    }

    @Test
    fun `findByKey should return correct record`() {
        val record = sampleRecord(
            key = "find-test-key",
            requestHash = "find-test-hash"
        )

        idempotencyKeyMapper.insertPending(record)
        val found = idempotencyKeyMapper.findByKey("find-test-key")

        assertNotNull(found)
        assertEquals("find-test-key", found!!.idempotencyKey)
        assertEquals("find-test-hash", found.requestHash)
        assertEquals(IdempotencyStatus.PENDING, found.status)
    }

    @Test
    fun `updatePaymentId should update payment_id field`() {
        val record = sampleRecord(
            key = "update-payment-key",
            requestHash = "update-hash"
        )

        idempotencyKeyMapper.insertPending(record)

        val paymentId = 12345L
        createPayment(paymentId)  // Create payment first to satisfy foreign key constraint
        val updated = idempotencyKeyMapper.updatePaymentId("update-payment-key", paymentId)
        assertEquals(1, updated, "Should update one row")

        val found = idempotencyKeyMapper.findByKey("update-payment-key")
        assertNotNull(found)
        assertEquals(paymentId, found!!.paymentId)
        assertEquals("update-hash", found.requestHash, "Other fields should remain unchanged")
        assertEquals(IdempotencyStatus.PENDING, found.status, "Status should remain PENDING")
    }

    @Test
    fun `updatePaymentId should return 0 for non-existent key`() {
        createPayment(999L)  // Create payment first
        val updated = idempotencyKeyMapper.updatePaymentId("non-existent", 999L)
        assertEquals(0, updated, "Should not update non-existent record")
    }

    @Test
    fun `updateResponsePayload should update payload and set status to COMPLETED`() {
        val record = sampleRecord(
            key = "complete-key",
            requestHash = "complete-hash"
        )

        idempotencyKeyMapper.insertPending(record)

        val responsePayload = """{"paymentId": "pub-123", "status": "AUTHORIZED"}"""
        val updated = idempotencyKeyMapper.updateResponsePayload("complete-key", responsePayload)
        assertEquals(1, updated, "Should update one row")

        val found = idempotencyKeyMapper.findByKey("complete-key")
        assertNotNull(found)
        assertJsonEquals(responsePayload, found!!.responsePayload)
        assertEquals(IdempotencyStatus.COMPLETED, found.status, "Status should be COMPLETED")
        assertEquals("complete-hash", found.requestHash, "Other fields should remain unchanged")
    }

    @Test
    fun `updateResponsePayload should return 0 for non-existent key`() {
        val updated = idempotencyKeyMapper.updateResponsePayload("non-existent", "{}")
        assertEquals(0, updated, "Should not update non-existent record")
    }

    @Test
    fun `full flow - insert, update payment id, then complete with payload`() {
        val key = "full-flow-key"
        val requestHash = "full-flow-hash"
        val record = sampleRecord(key = key, requestHash = requestHash)

        // Step 1: Insert pending
        val inserted = idempotencyKeyMapper.insertPending(record)
        assertEquals(1, inserted)

        var found = idempotencyKeyMapper.findByKey(key)
        assertNotNull(found)
        assertEquals(IdempotencyStatus.PENDING, found!!.status)
        assertNull(found.paymentId)
        assertNull(found.responsePayload)

        // Step 2: Update with payment ID
        val paymentId = 54321L
        createPayment(paymentId)  // Create payment first to satisfy foreign key constraint
        idempotencyKeyMapper.updatePaymentId(key, paymentId)

        found = idempotencyKeyMapper.findByKey(key)
        assertNotNull(found)
        assertEquals(paymentId, found!!.paymentId)
        assertEquals(IdempotencyStatus.PENDING, found.status, "Still PENDING after payment ID update")

        // Step 3: Complete with response payload
        val responsePayload = """{"paymentId": "pub-54321", "status": "AUTHORIZED", "orderId": "ORDER-123"}"""
        idempotencyKeyMapper.updateResponsePayload(key, responsePayload)

        found = idempotencyKeyMapper.findByKey(key)
        assertNotNull(found)
        assertEquals(paymentId, found!!.paymentId, "Payment ID should remain")
        assertJsonEquals(responsePayload, found.responsePayload)
        assertEquals(IdempotencyStatus.COMPLETED, found.status)
        assertEquals(requestHash, found.requestHash, "Request hash should remain unchanged")
    }

    @Test
    fun `multiple idempotency keys can coexist`() {
        val record1 = sampleRecord(key = "key-1", requestHash = "hash-1")
        val record2 = sampleRecord(key = "key-2", requestHash = "hash-2")
        val record3 = sampleRecord(key = "key-3", requestHash = "hash-3")

        idempotencyKeyMapper.insertPending(record1)
        idempotencyKeyMapper.insertPending(record2)
        idempotencyKeyMapper.insertPending(record3)

        val found1 = idempotencyKeyMapper.findByKey("key-1")
        val found2 = idempotencyKeyMapper.findByKey("key-2")
        val found3 = idempotencyKeyMapper.findByKey("key-3")

        assertNotNull(found1)
        assertNotNull(found2)
        assertNotNull(found3)

        assertEquals("hash-1", found1!!.requestHash)
        assertEquals("hash-2", found2!!.requestHash)
        assertEquals("hash-3", found3!!.requestHash)
    }

    @Test
    fun `updatePaymentId multiple times should update correctly`() {
        val record = sampleRecord(key = "multi-update-key", requestHash = "multi-hash")
        idempotencyKeyMapper.insertPending(record)

        // First update
        createPayment(111L)  // Create payment first
        idempotencyKeyMapper.updatePaymentId("multi-update-key", 111L)
        var found = idempotencyKeyMapper.findByKey("multi-update-key")
        assertEquals(111L, found!!.paymentId)

        // Second update
        createPayment(222L)  // Create payment first
        idempotencyKeyMapper.updatePaymentId("multi-update-key", 222L)
        found = idempotencyKeyMapper.findByKey("multi-update-key")
        assertEquals(222L, found!!.paymentId, "Should update to new payment ID")
    }

    @Test
    fun `created_at should be set automatically`() {
        val record = sampleRecord(key = "timestamp-key", requestHash = "timestamp-hash")
        idempotencyKeyMapper.insertPending(record)

        val found = idempotencyKeyMapper.findByKey("timestamp-key")
        assertNotNull(found)
        assertNotNull(found!!.createdAt)
        
        // Created at should be recent (within last minute)
        val now = Utc.nowInstant().normalizeToMicroseconds()
        val createdAt = found.createdAt.normalizeToMicroseconds()
        assertTrue(createdAt.isBefore(now) || createdAt.equals(now), "Created at should be in the past or now")
    }
}

