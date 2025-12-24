package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentIntentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentIntentMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
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
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.serialization.JacksonUtil
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant

@Tag("integration")
@MybatisTest
@ContextConfiguration(classes = [InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=false"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis")
class PaymentIntentMapperIntegrationTest {

    /**
     * Normalizes Instant to microsecond precision to match PostgreSQL's TIMESTAMP precision.
     * PostgreSQL stores timestamps with microsecond precision (6 decimal places),
     * but Java Instant can have nanosecond precision (9 decimal places).
     */
    private fun Instant.normalizeToMicroseconds(): Instant {
        return this.truncatedTo(java.time.temporal.ChronoUnit.MICROS)
    }

    /**
     * Normalizes PaymentIntentEntity timestamps to microsecond precision for comparison.
     */
    private fun PaymentIntentEntity.normalizeTimestamps(): PaymentIntentEntity {
        return this.copy(
            createdAt = this.createdAt.normalizeToMicroseconds(),
            updatedAt = this.updatedAt.normalizeToMicroseconds()
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
                PaymentIntentMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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
    lateinit var paymentIntentMapper: PaymentIntentMapper

    private val objectMapper: ObjectMapper = JacksonUtil.createObjectMapper()

    private fun sampleEntity(id: Long = 101L, status: String = "CREATED_PENDING"): PaymentIntentEntity {
        val now = Utc.nowInstant().normalizeToMicroseconds()
        val paymentOrderLines = listOf(
            PaymentOrderLine(
                sellerId = SellerId("seller-1"),
                amount = Amount.of(10_000, Currency("USD"))
            )
        )
        return PaymentIntentEntity(
            paymentIntentId = id,
            pspReference = "", // Default empty string for new payment intents
            buyerId = "buyer-$id",
            orderId = "order-$id",
            totalAmountValue = 10_000,
            currency = "USD",
            status = status,
            createdAt = now,
            updatedAt = now,
            paymentLinesJson = objectMapper.writeValueAsString(paymentOrderLines)
        )
    }

    @Test
    fun `insert and findById and getMaxPaymentIntentId`() {
        // given
        val entity = sampleEntity(101L)

        // when
        val rows = paymentIntentMapper.insert(entity)

        // then
        assertEquals(1, rows)

        val loaded = paymentIntentMapper.findById(101L)
        assertNotNull(loaded)

        // normalize timestamps to match Postgres precision
        val normalizedEntity = entity.normalizeTimestamps()
        val normalizedLoaded = loaded!!.normalizeTimestamps()
        
        // Compare all fields except paymentLinesJson (JSON property order may differ)
        assertEquals(normalizedEntity.paymentIntentId, normalizedLoaded.paymentIntentId)
        assertEquals(normalizedEntity.buyerId, normalizedLoaded.buyerId)
        assertEquals(normalizedEntity.orderId, normalizedLoaded.orderId)
        assertEquals(normalizedEntity.totalAmountValue, normalizedLoaded.totalAmountValue)
        assertEquals(normalizedEntity.currency, normalizedLoaded.currency)
        assertEquals(normalizedEntity.status, normalizedLoaded.status)
        assertEquals(normalizedEntity.createdAt, normalizedLoaded.createdAt)
        assertEquals(normalizedEntity.updatedAt, normalizedLoaded.updatedAt)
        
        // Compare JSON by parsing to JSON nodes (ignores property order)
        val expectedJsonNode = objectMapper.readTree(normalizedEntity.paymentLinesJson)
        val loadedJsonNode = objectMapper.readTree(normalizedLoaded.paymentLinesJson)
        assertEquals(expectedJsonNode, loadedJsonNode)

        val maxId = paymentIntentMapper.getMaxPaymentIntentId()
        assertEquals(101L, maxId)
    }

    @Test
    fun `update and deleteById`() {
        // given
        val entity = sampleEntity(201L)
        paymentIntentMapper.insert(entity)

        // when – update some fields
        val updatedEntity = entity.copy(
            status = "AUTHORIZED",
            updatedAt = Utc.nowInstant().normalizeToMicroseconds()
        )
        val updatedRows = paymentIntentMapper.update(updatedEntity)

        // then
        assertEquals(1, updatedRows)

        val reloaded = paymentIntentMapper.findById(201L)
        assertNotNull(reloaded)
        assertEquals("AUTHORIZED", reloaded!!.status)

        // when – delete by id
        val deletedRows = paymentIntentMapper.deleteById(201L)
        assertEquals(1, deletedRows)

        val afterDelete = paymentIntentMapper.findById(201L)
        assertNull(afterDelete)
    }

    @Test
    fun `tryMarkPendingAuth updates status from CREATED to PENDING_AUTH`() {
        // given - First create with CREATED_PENDING, then update to CREATED
        val entity = sampleEntity(301L, "CREATED_PENDING")
        paymentIntentMapper.insert(entity)
        
        // Update to CREATED status (simulating markAsCreated)
        val createdEntity = entity.copy(status = "CREATED")
        paymentIntentMapper.update(createdEntity)
        
        val originalUpdatedAt = createdEntity.updatedAt
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        
        // Ensure there's a time difference
        Thread.sleep(10)
        
        // when
        val updatedRows = paymentIntentMapper.tryMarkPendingAuth(301L, newUpdatedAt)
        
        // then
        assertEquals(1, updatedRows, "Should update exactly one row")
        
        val reloaded = paymentIntentMapper.findById(301L)
        assertNotNull(reloaded)
        assertEquals("PENDING_AUTH", reloaded!!.status, "Status should be updated to PENDING_AUTH")
        assertEquals(newUpdatedAt, reloaded.updatedAt.normalizeToMicroseconds(), "updated_at should be updated")
    }

    @Test
    fun `tryMarkPendingAuth does not update if status is not CREATED`() {
        // given - Test with CREATED_PENDING status (should not update)
        val entity = sampleEntity(401L, "CREATED_PENDING")
        paymentIntentMapper.insert(entity)
        
        val originalUpdatedAt = entity.updatedAt
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        
        // Ensure there's a time difference
        Thread.sleep(10)
        
        // when
        val updatedRows = paymentIntentMapper.tryMarkPendingAuth(401L, newUpdatedAt)
        
        // then
        assertEquals(0, updatedRows, "Should not update any rows when status is CREATED_PENDING (not CREATED)")
        
        val reloaded = paymentIntentMapper.findById(401L)
        assertNotNull(reloaded)
        assertEquals("CREATED_PENDING", reloaded!!.status, "Status should remain CREATED_PENDING")
        assertEquals(originalUpdatedAt, reloaded.updatedAt.normalizeToMicroseconds(), "updated_at should not change")
        
        // Also test with AUTHORIZED status
        val authorizedEntity = entity.copy(status = "AUTHORIZED", updatedAt = Utc.nowInstant().normalizeToMicroseconds())
        paymentIntentMapper.update(authorizedEntity)
        
        val updatedRows2 = paymentIntentMapper.tryMarkPendingAuth(401L, newUpdatedAt)
        assertEquals(0, updatedRows2, "Should not update any rows when status is AUTHORIZED")
        
        val reloaded2 = paymentIntentMapper.findById(401L)
        assertEquals("AUTHORIZED", reloaded2!!.status, "Status should remain AUTHORIZED")
    }

    @Test
    fun `tryMarkPendingAuth does not update non-existent payment intent`() {
        // given
        val nonExistentId = 999L
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        
        // when
        val updatedRows = paymentIntentMapper.tryMarkPendingAuth(nonExistentId, newUpdatedAt)
        
        // then
        assertEquals(0, updatedRows, "Should not update any rows for non-existent payment intent")
    }

    @Test
    fun `updatePspReference updates psp_reference and updated_at`() {
        // given
        val entity = sampleEntity(501L, "CREATED_PENDING")
        paymentIntentMapper.insert(entity)
        
        val originalUpdatedAt = entity.updatedAt
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        val pspReference = "pi_3ShY7NEAJKUKtoJw1h8nCnIC"
        
        // Ensure there's a time difference
        Thread.sleep(10)
        
        // when
        val updatedRows = paymentIntentMapper.updatePspReference(501L, pspReference, newUpdatedAt)
        
        // then
        assertEquals(1, updatedRows, "Should update exactly one row")
        
        val reloaded = paymentIntentMapper.findById(501L)
        assertNotNull(reloaded)
        assertEquals(pspReference, reloaded!!.pspReference, "psp_reference should be updated")
        assertEquals(newUpdatedAt, reloaded.updatedAt.normalizeToMicroseconds(), "updated_at should be updated")
        assertEquals("CREATED_PENDING", reloaded.status, "Status should remain unchanged")
    }

    @Test
    fun `updatePspReference does not update non-existent payment intent`() {
        // given
        val nonExistentId = 999L
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        val pspReference = "pi_3ShY7NEAJKUKtoJw1h8nCnIC"
        
        // when
        val updatedRows = paymentIntentMapper.updatePspReference(nonExistentId, pspReference, newUpdatedAt)
        
        // then
        assertEquals(0, updatedRows, "Should not update any rows for non-existent payment intent")
    }

    @Test
    fun `updatePspReference can update psp_reference multiple times`() {
        // given
        val entity = sampleEntity(601L, "CREATED")
        paymentIntentMapper.insert(entity)
        
        val firstPspReference = "pi_3ShY7NEAJKUKtoJw1h8nCnIC"
        val firstUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        Thread.sleep(10)
        
        // when - first update
        val firstUpdateRows = paymentIntentMapper.updatePspReference(601L, firstPspReference, firstUpdatedAt)
        assertEquals(1, firstUpdateRows)
        
        val afterFirstUpdate = paymentIntentMapper.findById(601L)
        assertEquals(firstPspReference, afterFirstUpdate!!.pspReference)
        
        // when - second update with different psp_reference
        Thread.sleep(10)
        val secondPspReference = "pi_3ShY7NEAJKUKtoJw1h8nCnID"
        val secondUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        val secondUpdateRows = paymentIntentMapper.updatePspReference(601L, secondPspReference, secondUpdatedAt)
        assertEquals(1, secondUpdateRows)
        
        // then
        val afterSecondUpdate = paymentIntentMapper.findById(601L)
        assertEquals(secondPspReference, afterSecondUpdate!!.pspReference, "psp_reference should be updated to new value")
        assertEquals(secondUpdatedAt, afterSecondUpdate.updatedAt.normalizeToMicroseconds(), "updated_at should be updated")
    }
}

