package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

/**
 * Integration tests for PaymentOrderMapper with real PostgreSQL (Testcontainers).
 * 
 * These tests validate:
 * - Real database persistence operations
 * - MyBatis mapper integration
 * - Payment order CRUD operations
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
class PaymentOrderMapperIntegrationTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl =
                PaymentOrderMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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
    lateinit var paymentOrderMapper: PaymentOrderMapper

    @Test
    fun `insert operation works correctly`() {
        val paymentOrderId = PaymentOrderId(2L)
        val paymentId = PaymentId(200L)
        val sellerId = SellerId("seller-abc")
        val now = LocalDateTime.now()

        // Test INSERT operation
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("PO-0002")
            .paymentId(paymentId)
            .publicPaymentId("PAY-200")
            .sellerId(sellerId)
            .amount(Amount.of(2020000L, Currency("USD")))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildNew()
        val insertResult = paymentOrderMapper.insertAllIgnore(listOf(PaymentOrderEntityMapper.toEntity(paymentOrder)))
        assertEquals(1, insertResult, "Insert should succeed")

        // Verify data was persisted
        val persisted = paymentOrderMapper.findByPaymentOrderId(paymentOrderId.value).first()
        assertEquals(paymentOrderId.value, persisted.paymentOrderId)
        assertEquals("PO-0002", persisted.publicPaymentOrderId)
        assertEquals(paymentId.value, persisted.paymentId)
        assertEquals("PAY-200", persisted.publicPaymentId)
        assertEquals(sellerId.value, persisted.sellerId)
        assertEquals(2020000L, persisted.amountValue)
        assertEquals("USD", persisted.amountCurrency)
    }

    @Test
    fun `update operation works correctly`() {
        val paymentOrderId = PaymentOrderId(3L)
        val paymentId = PaymentId(300L)
        val sellerId = SellerId("seller-xyz")
        val now = LocalDateTime.now()

        // First INSERT
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("PO-0003")
            .paymentId(paymentId)
            .publicPaymentId("PAY-300")
            .sellerId(sellerId)
            .amount(Amount.of(3030000L, Currency("USD")))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildNew()
        paymentOrderMapper.insertAllIgnore(listOf(PaymentOrderEntityMapper.toEntity(paymentOrder)))

        // Test UPDATE operation
        val updatedOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("PO-0003")
            .paymentId(paymentId)
            .publicPaymentId("PAY-300")
            .sellerId(sellerId)
            .amount(Amount.of(3030000L, Currency("USD")))
            .status(PaymentOrderStatus.SUCCESSFUL_FINAL)
            .createdAt(now.minusDays(1))
            .updatedAt(now)
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
        val updateResult = paymentOrderMapper.updateReturningIdempotent(PaymentOrderEntityMapper.toEntity(updatedOrder))
        assertNotNull(updateResult, "Update should succeed and return updated entity")

        // Verify data was updated
        val persisted = paymentOrderMapper.findByPaymentOrderId(paymentOrderId.value).first()
        assertEquals(PaymentOrderStatus.SUCCESSFUL_FINAL, persisted.status)
        assertEquals(now, persisted.updatedAt)
    }

    @Test
    fun `bulk insert with ignore works correctly`() {
        val paymentOrders = (1..5).map { i ->
            PaymentOrder.builder()
                .paymentOrderId(PaymentOrderId(100L + i))
                .publicPaymentOrderId("PO-bulk-$i")
                .paymentId(PaymentId(200L + i))
                .publicPaymentId("PAY-bulk-$i")
                .sellerId(SellerId("seller-bulk"))
                .amount(Amount.of(10000L * i, Currency("USD")))
                .status(PaymentOrderStatus.INITIATED_PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .retryCount(0)
                .retryReason(null)
                .lastErrorMessage(null)
                .buildNew()
        }

        val entities = paymentOrders.map { PaymentOrderEntityMapper.toEntity(it) }
        val insertedCount = paymentOrderMapper.insertAllIgnore(entities)

        assertEquals(5, insertedCount)

        // Verify all were inserted by checking each ID
        val allOrders = (101L..105L).flatMap { paymentOrderMapper.findByPaymentOrderId(it) }
        assertTrue(allOrders.isNotEmpty())
        assertEquals(5, allOrders.size)
    }

    @Test
    fun `duplicate insert with ignore does not fail`() {
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(500L))
            .publicPaymentOrderId("PO-duplicate")
            .paymentId(PaymentId(600L))
            .publicPaymentId("PAY-duplicate")
            .sellerId(SellerId("seller-duplicate"))
            .amount(Amount.of(50000L, Currency("USD")))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildNew()

        val entity = PaymentOrderEntityMapper.toEntity(paymentOrder)

        // First insert should succeed
        val firstInsert = paymentOrderMapper.insertAllIgnore(listOf(entity))
        assertEquals(1, firstInsert)

        // Second insert with same ID should be ignored
        val secondInsert = paymentOrderMapper.insertAllIgnore(listOf(entity))
        assertEquals(0, secondInsert) // No rows inserted due to ignore

        // Should still have only one record
        val orders = paymentOrderMapper.findByPaymentOrderId(500L)
        assertEquals(1, orders.size)
    }

    @Test
    fun `findByPaymentOrderId returns correct data`() {
        val paymentOrderId = PaymentOrderId(700L)
        val paymentId = PaymentId(800L)
        val sellerId = SellerId("seller-find")
        val amount = Amount.of(75000L, Currency("EUR"))
        val now = LocalDateTime.now()

        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("PO-find-700")
            .paymentId(paymentId)
            .publicPaymentId("PAY-find-800")
            .sellerId(sellerId)
            .amount(amount)
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(now)
            .updatedAt(now)
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildNew()

        paymentOrderMapper.insertAllIgnore(listOf(PaymentOrderEntityMapper.toEntity(paymentOrder)))

        val found = paymentOrderMapper.findByPaymentOrderId(paymentOrderId.value)
        assertEquals(1, found.size)

        val entity = found.first()
        assertEquals(paymentOrderId.value, entity.paymentOrderId)
        assertEquals("PO-find-700", entity.publicPaymentOrderId)
        assertEquals(paymentId.value, entity.paymentId)
        assertEquals("PAY-find-800", entity.publicPaymentId)
        assertEquals(sellerId.value, entity.sellerId)
        assertEquals(amount.quantity, entity.amountValue)
        assertEquals(amount.currency.currencyCode, entity.amountCurrency)
        assertEquals(PaymentOrderStatus.INITIATED_PENDING, entity.status)
    }

    @Test
    fun `updateReturningIdempotent handles multiple updates correctly`() {
        val paymentOrderId = PaymentOrderId(900L)
        val paymentId = PaymentId(1000L)
        val sellerId = SellerId("seller-concurrent")
        val now = LocalDateTime.now()

        // First INSERT
        val init = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("PO-concurrent-900")
            .paymentId(paymentId)
            .publicPaymentId("PAY-concurrent-1000")
            .sellerId(sellerId)
            .amount(Amount.of(90000L, Currency("USD")))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(now)
            .updatedAt(now)
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildNew()
        paymentOrderMapper.insertAllIgnore(listOf(PaymentOrderEntityMapper.toEntity(init)))

        // First UPDATE
        val firstUpdate = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("PO-concurrent-900")
            .paymentId(paymentId)
            .publicPaymentId("PAY-concurrent-1000")
            .sellerId(sellerId)
            .amount(Amount.of(90000L, Currency("USD")))
            .status(PaymentOrderStatus.SUCCESSFUL_FINAL)
            .createdAt(now)
            .updatedAt(now.plusMinutes(1))
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
        val firstResult = paymentOrderMapper.updateReturningIdempotent(PaymentOrderEntityMapper.toEntity(firstUpdate))
        assertNotNull(firstResult, "First update should succeed")

        // Second UPDATE (should be ignored due to idempotent behavior)
        val secondUpdate = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("PO-concurrent-900")
            .paymentId(paymentId)
            .publicPaymentId("PAY-concurrent-1000")
            .sellerId(sellerId)
            .amount(Amount.of(90000L, Currency("USD")))
            .status(PaymentOrderStatus.FAILED_FINAL)
            .createdAt(now)
            .updatedAt(now.plusMinutes(2))
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
        val secondResult = paymentOrderMapper.updateReturningIdempotent(PaymentOrderEntityMapper.toEntity(secondUpdate))
        assertNull(secondResult, "Second update should be ignored (idempotent)")

        // Verify final state
        val finalEntity = paymentOrderMapper.findByPaymentOrderId(paymentOrderId.value).first()
        assertEquals(PaymentOrderStatus.SUCCESSFUL_FINAL, finalEntity.status)
        assertEquals(now.plusMinutes(1), finalEntity.updatedAt)
    }

    @Test
    fun `database insert and query operations work correctly`() {
        val paymentOrderId = PaymentOrderId(1100L)
        val paymentId = PaymentId(1200L)
        val sellerId = SellerId("seller-transaction")

        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("PO-transaction-1100")
            .paymentId(paymentId)
            .publicPaymentId("PAY-transaction-1200")
            .sellerId(sellerId)
            .amount(Amount.of(110000L, Currency("USD")))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildNew()

        // Test that we can insert data
        paymentOrderMapper.insertAllIgnore(listOf(PaymentOrderEntityMapper.toEntity(paymentOrder)))

        // Verify data was inserted
        val found = paymentOrderMapper.findByPaymentOrderId(paymentOrderId.value)
        assertEquals(1, found.size, "Expected 1 record to be inserted")

        // Verify the data has correct values
        val entity = found.first()
        assertEquals(paymentOrderId.value, entity.paymentOrderId)
        assertEquals("PO-transaction-1100", entity.publicPaymentOrderId)
        assertEquals(PaymentOrderStatus.INITIATED_PENDING, entity.status)
    }
}