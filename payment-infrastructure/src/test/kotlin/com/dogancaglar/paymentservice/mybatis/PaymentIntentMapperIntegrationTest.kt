package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentIntentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentIntentMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
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

    @BeforeEach
    fun cleanup() {
        paymentIntentMapper.deleteAll()
    }

    private fun sampleEntity(
        id: Long = 101L,
        status: String = "CREATED_PENDING",
        pspReference: String? = null
    ): PaymentIntentEntity {
        val now = Utc.nowInstant().normalizeToMicroseconds()
        val paymentOrderLines = listOf(
            PaymentOrderLine(
                sellerId = SellerId("seller-1"),
                amount = Amount.of(10_000, Currency("USD"))
            )
        )
        return PaymentIntentEntity(
            paymentIntentId = id,
            pspReference = pspReference,
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
    fun `getMaxPaymentIntentId returns 0 when table is empty`() {
        // when
        val maxId = paymentIntentMapper.getMaxPaymentIntentId()

        // then
        assertEquals(0L, maxId)
    }

    @Test
    fun `getMaxPaymentIntentId returns max ID when table has data`() {
        // given
        paymentIntentMapper.insert(sampleEntity(100L))
        paymentIntentMapper.insert(sampleEntity(200L))
        paymentIntentMapper.insert(sampleEntity(150L))

        // when
        val maxId = paymentIntentMapper.getMaxPaymentIntentId()

        // then
        assertEquals(200L, maxId)
    }

    @Test
    fun `insert and findById with null pspReference`() {
        // given
        val entity = sampleEntity(101L, "CREATED_PENDING", null)

        // when
        val rows = paymentIntentMapper.insert(entity)
        val loaded = paymentIntentMapper.findById(101L)

        // then
        assertEquals(1, rows)
        assertNotNull(loaded)

        val normalizedEntity = entity.normalizeTimestamps()
        val normalizedLoaded = loaded!!.normalizeTimestamps()

        assertEquals(normalizedEntity.paymentIntentId, normalizedLoaded.paymentIntentId)
        assertNull(normalizedEntity.pspReference, "Original entity should have null pspReference")
        assertNull(normalizedLoaded.pspReference, "Loaded entity should have null pspReference (not empty string)")
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
    }

    @Test
    fun `insert and findById with pspReference set`() {
        // given
        val pspRef = "pi_3ShY7NEAJKUKtoJw1h8nCnIC"
        val entity = sampleEntity(102L, "CREATED", pspRef)

        // when
        paymentIntentMapper.insert(entity)
        val loaded = paymentIntentMapper.findById(102L)

        // then
        assertNotNull(loaded)
        assertEquals(pspRef, loaded!!.pspReference)
        assertEquals("CREATED", loaded.status)
    }

    @Test
    fun `findById returns null for non-existent ID`() {
        // when
        val loaded = paymentIntentMapper.findById(999L)

        // then
        assertNull(loaded)
    }

    @Test
    fun `update all fields including pspReference`() {
        // given
        val entity = sampleEntity(201L, "CREATED_PENDING", null)
        paymentIntentMapper.insert(entity)

        // when - update all fields
        val newPspRef = "pi_updated123"
        val newStatus = "AUTHORIZED"
        val newBuyerId = "buyer-updated"
        val newOrderId = "order-updated"
        val newTotalAmount = 20_000L
        val newCurrency = "EUR"
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()

        Thread.sleep(10) // Ensure time difference

        val updatedEntity = entity.copy(
            pspReference = newPspRef,
            status = newStatus,
            buyerId = newBuyerId,
            orderId = newOrderId,
            totalAmountValue = newTotalAmount,
            currency = newCurrency,
            updatedAt = newUpdatedAt
        )
        val updatedRows = paymentIntentMapper.update(updatedEntity)

        // then
        assertEquals(1, updatedRows)

        val reloaded = paymentIntentMapper.findById(201L)
        assertNotNull(reloaded)
        assertEquals(newPspRef, reloaded!!.pspReference, "pspReference should be updated")
        assertEquals(newStatus, reloaded.status, "status should be updated")
        assertEquals(newBuyerId, reloaded.buyerId, "buyerId should be updated")
        assertEquals(newOrderId, reloaded.orderId, "orderId should be updated")
        assertEquals(newTotalAmount, reloaded.totalAmountValue, "totalAmountValue should be updated")
        assertEquals(newCurrency, reloaded.currency, "currency should be updated")
        assertEquals(newUpdatedAt, reloaded.updatedAt.normalizeToMicroseconds(), "updatedAt should be updated")
    }

    @Test
    fun `update pspReference from null to value`() {
        // given
        val entity = sampleEntity(301L, "CREATED_PENDING", null)
        paymentIntentMapper.insert(entity)

        // when
        val newPspRef = "pi_new123"
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        Thread.sleep(10)

        val updatedEntity = entity.copy(
            pspReference = newPspRef,
            status = "CREATED",
            updatedAt = newUpdatedAt
        )
        paymentIntentMapper.update(updatedEntity)

        // then
        val reloaded = paymentIntentMapper.findById(301L)
        assertNotNull(reloaded)
        assertEquals(newPspRef, reloaded!!.pspReference, "pspReference should be updated from null to value")
        assertEquals("CREATED", reloaded.status)
    }

    @Test
    fun `update pspReference from value to null`() {
        // given
        val entity = sampleEntity(302L, "CREATED", "pi_old123")
        paymentIntentMapper.insert(entity)

        // when
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        Thread.sleep(10)

        val updatedEntity = entity.copy(
            pspReference = null,
            updatedAt = newUpdatedAt
        )
        paymentIntentMapper.update(updatedEntity)

        // then
        val reloaded = paymentIntentMapper.findById(302L)
        assertNotNull(reloaded)
        assertNull(reloaded!!.pspReference, "pspReference should be updated from value to null")
    }

    @Test
    fun `tryMarkPendingAuth updates status from CREATED to PENDING_AUTH`() {
        // given
        val entity = sampleEntity(401L, "CREATED_PENDING", null)
        paymentIntentMapper.insert(entity)

        // First update to CREATED status
        val createdEntity = entity.copy(status = "CREATED")
        paymentIntentMapper.update(createdEntity)

        val originalUpdatedAt = createdEntity.updatedAt
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        Thread.sleep(10)

        // when
        val updatedRows = paymentIntentMapper.tryMarkPendingAuth(401L, newUpdatedAt)

        // then
        assertEquals(1, updatedRows, "Should update exactly one row")

        val reloaded = paymentIntentMapper.findById(401L)
        assertNotNull(reloaded)
        assertEquals("PENDING_AUTH", reloaded!!.status, "Status should be updated to PENDING_AUTH")
        assertEquals(newUpdatedAt, reloaded.updatedAt.normalizeToMicroseconds(), "updated_at should be updated")
    }

    @Test
    fun `tryMarkPendingAuth does not update if status is not CREATED`() {
        // given - Test with CREATED_PENDING status (should not update)
        val entity = sampleEntity(501L, "CREATED_PENDING", null)
        paymentIntentMapper.insert(entity)

        val originalUpdatedAt = entity.updatedAt
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        Thread.sleep(10)

        // when
        val updatedRows = paymentIntentMapper.tryMarkPendingAuth(501L, newUpdatedAt)

        // then
        assertEquals(0, updatedRows, "Should not update any rows when status is CREATED_PENDING (not CREATED)")

        val reloaded = paymentIntentMapper.findById(501L)
        assertNotNull(reloaded)
        assertEquals("CREATED_PENDING", reloaded!!.status, "Status should remain CREATED_PENDING")
        assertEquals(originalUpdatedAt, reloaded.updatedAt.normalizeToMicroseconds(), "updated_at should not change")

        // Also test with AUTHORIZED status
        val authorizedEntity = entity.copy(status = "AUTHORIZED", updatedAt = Utc.nowInstant().normalizeToMicroseconds())
        paymentIntentMapper.update(authorizedEntity)

        val updatedRows2 = paymentIntentMapper.tryMarkPendingAuth(501L, newUpdatedAt)
        assertEquals(0, updatedRows2, "Should not update any rows when status is AUTHORIZED")

        val reloaded2 = paymentIntentMapper.findById(501L)
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
    fun `updatePspReference updates psp_reference from null to value`() {
        // given
        val entity = sampleEntity(601L, "CREATED_PENDING", null)
        paymentIntentMapper.insert(entity)

        val originalUpdatedAt = entity.updatedAt
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        val pspReference = "pi_3ShY7NEAJKUKtoJw1h8nCnIC"

        Thread.sleep(10)

        // when
        val updatedRows = paymentIntentMapper.updatePspReference(601L, pspReference, newUpdatedAt)

        // then
        assertEquals(1, updatedRows, "Should update exactly one row")

        val reloaded = paymentIntentMapper.findById(601L)
        assertNotNull(reloaded)
        assertEquals(pspReference, reloaded!!.pspReference, "psp_reference should be updated from null to value")
        assertEquals(newUpdatedAt, reloaded.updatedAt.normalizeToMicroseconds(), "updated_at should be updated")
        assertEquals("CREATED_PENDING", reloaded.status, "Status should remain unchanged")
    }

    @Test
    fun `updatePspReference updates psp_reference from value to new value`() {
        // given
        val originalPspRef = "pi_old123"
        val entity = sampleEntity(602L, "CREATED", originalPspRef)
        paymentIntentMapper.insert(entity)

        val originalUpdatedAt = entity.updatedAt
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        val newPspReference = "pi_new456"

        Thread.sleep(10)

        // when
        val updatedRows = paymentIntentMapper.updatePspReference(602L, newPspReference, newUpdatedAt)

        // then
        assertEquals(1, updatedRows, "Should update exactly one row")

        val reloaded = paymentIntentMapper.findById(602L)
        assertNotNull(reloaded)
        assertEquals(newPspReference, reloaded!!.pspReference, "psp_reference should be updated to new value")
        assertEquals(newUpdatedAt, reloaded.updatedAt.normalizeToMicroseconds(), "updated_at should be updated")
        assertEquals("CREATED", reloaded.status, "Status should remain unchanged")
    }

    @Test
    fun `updatePspReference does not update non-existent payment intent`() {
        // given
        val nonExistentId = 999L
        val newUpdatedAt = Utc.nowInstant().normalizeToMicroseconds()
        val pspReference = "pi_123"

        // when
        val updatedRows = paymentIntentMapper.updatePspReference(nonExistentId, pspReference, newUpdatedAt)

        // then
        assertEquals(0, updatedRows, "Should not update any rows for non-existent payment intent")
    }

    @Test
    fun `deleteById removes existing payment intent`() {
        // given
        val entity = sampleEntity(701L)
        paymentIntentMapper.insert(entity)

        // verify it exists
        val beforeDelete = paymentIntentMapper.findById(701L)
        assertNotNull(beforeDelete)

        // when
        val deletedRows = paymentIntentMapper.deleteById(701L)

        // then
        assertEquals(1, deletedRows, "Should delete exactly one row")

        val afterDelete = paymentIntentMapper.findById(701L)
        assertNull(afterDelete, "Payment intent should be deleted")
    }

    @Test
    fun `deleteById returns 0 for non-existent payment intent`() {
        // when
        val deletedRows = paymentIntentMapper.deleteById(999L)

        // then
        assertEquals(0, deletedRows, "Should return 0 for non-existent payment intent")
    }

    @Test
    fun `deleteAll clears entire table`() {
        // given
        paymentIntentMapper.insert(sampleEntity(801L))
        paymentIntentMapper.insert(sampleEntity(802L))
        paymentIntentMapper.insert(sampleEntity(803L))

        // verify they exist
        assertNotNull(paymentIntentMapper.findById(801L))
        assertNotNull(paymentIntentMapper.findById(802L))
        assertNotNull(paymentIntentMapper.findById(803L))

        // when
        val deletedRows = paymentIntentMapper.deleteAll()

        // then
        assertEquals(3, deletedRows, "Should delete all 3 rows")

        val maxId = paymentIntentMapper.getMaxPaymentIntentId()
        assertEquals(0L, maxId, "Max ID should be 0 after deleteAll")

        val allDeleted = paymentIntentMapper.findById(801L)
        assertNull(allDeleted, "All payment intents should be deleted")
    }

    @Test
    fun `complete lifecycle insert CREATED_PENDING with null pspReference update to CREATED with pspReference tryMarkPendingAuth then update`() {
        // given - Insert with CREATED_PENDING and null pspReference
        val entity = sampleEntity(901L, "CREATED_PENDING", null)
        paymentIntentMapper.insert(entity)

        var loaded = paymentIntentMapper.findById(901L)
        assertNotNull(loaded)
        assertNull(loaded!!.pspReference)
        assertEquals("CREATED_PENDING", loaded.status)

        // when - Update to CREATED with pspReference
        val pspRef = "pi_lifecycle123"
        val updatedAt1 = Utc.nowInstant().normalizeToMicroseconds()
        Thread.sleep(10)

        val createdEntity = entity.copy(
            status = "CREATED",
            pspReference = pspRef,
            updatedAt = updatedAt1
        )
        paymentIntentMapper.update(createdEntity)

        loaded = paymentIntentMapper.findById(901L)
        assertNotNull(loaded)
        assertEquals(pspRef, loaded!!.pspReference)
        assertEquals("CREATED", loaded.status)

        // when - Use tryMarkPendingAuth to transition to PENDING_AUTH
        val updatedAt2 = Utc.nowInstant().normalizeToMicroseconds()
        Thread.sleep(10)

        val authRows = paymentIntentMapper.tryMarkPendingAuth(901L, updatedAt2)
        assertEquals(1, authRows)

        loaded = paymentIntentMapper.findById(901L)
        assertNotNull(loaded)
        assertEquals("PENDING_AUTH", loaded!!.status)
        assertEquals(pspRef, loaded.pspReference, "pspReference should remain unchanged")
        assertEquals(updatedAt2, loaded.updatedAt.normalizeToMicroseconds())

        // when - Final update to AUTHORIZED
        val updatedAt3 = Utc.nowInstant().normalizeToMicroseconds()
        Thread.sleep(10)

        val authorizedEntity = loaded.copy(
            status = "AUTHORIZED",
            updatedAt = updatedAt3
        )
        paymentIntentMapper.update(authorizedEntity)

        loaded = paymentIntentMapper.findById(901L)
        assertNotNull(loaded)
        assertEquals("AUTHORIZED", loaded!!.status)
        assertEquals(pspRef, loaded.pspReference, "pspReference should remain unchanged")
        assertEquals(updatedAt3, loaded.updatedAt.normalizeToMicroseconds())
    }
}
