package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentOrderMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

@Tag("integration")
@MybatisTest
@ContextConfiguration(classes = [InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=false"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis")
class PaymentOrderMapperIntegrationTest {

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
                PaymentOrderMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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
    lateinit var paymentMapper: PaymentMapper

    @Autowired
    lateinit var paymentOrderMapper: PaymentOrderMapper

    private fun upsertPayment(paymentId: Long) {
        val now = LocalDateTime.now().withNano(0)
        paymentMapper.insertIgnore(
            PaymentEntity(
                paymentId = paymentId,
                buyerId = "buyer-$paymentId",
                orderId = "order-$paymentId",
                totalAmountValue = 50_00,
                capturedAmountValue = 0,
                currency = "USD",
                status = "PENDING_AUTH",

                idempotencyKey = "12345",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun paymentOrderEntity(
        id: Long,
        paymentId: Long = 1001L,
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING,
        retryCount: Int = 0,
        createdAt: LocalDateTime = LocalDateTime.now().withNano(0),
        updatedAt: LocalDateTime = createdAt
    ) = PaymentOrderEntity(
        paymentOrderId = id,
        paymentId = paymentId,
        sellerId = "seller-$paymentId",
        amountValue = 10_00,
        amountCurrency = "USD",
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        retryCount = retryCount
    )

    @Test
    fun `basic CRUD and counters`() {
        upsertPayment(1001L)

        val entity = paymentOrderEntity(201L)
        val inserted = paymentOrderMapper.insert(entity)
        assertEquals(1, inserted)

        val byId = paymentOrderMapper.findByPaymentOrderId(201L)
        assertEquals(1, byId.size)
        assertEquals(entity, byId.first())

        val byPayment = paymentOrderMapper.findByPaymentId(1001L)
        assertEquals(1, byPayment.size)

        assertEquals(1L, paymentOrderMapper.countByPaymentId(1001L))
        assertEquals(
            1L,
            paymentOrderMapper.countByPaymentIdAndStatusIn(
                paymentId = 1001L,
                statuses = listOf(PaymentOrderStatus.INITIATED_PENDING.name)
            )
        )
        assertEquals(
            false,
            paymentOrderMapper.existsByPaymentIdAndStatus(
                1001L,
                PaymentOrderStatus.CAPTURED.name
            )
        )
        assertEquals(
            true,
            paymentOrderMapper.existsByPaymentIdAndStatus(
                1001L,
                PaymentOrderStatus.INITIATED_PENDING.name
            )
        )

        assertEquals(201L, paymentOrderMapper.getMaxPaymentOrderId())
    }

    @Test
    fun `updateReturningIdempotent respects terminal statuses`() {
        upsertPayment(2001L)
        val base = paymentOrderEntity(
            id = 301L,
            paymentId = 2001L,
            status = PaymentOrderStatus.CAPTURE_REQUESTED,
            retryCount = 1
        )
        paymentOrderMapper.insert(base)

        val updated = paymentOrderMapper.updateReturningIdempotent(
            base.copy(
                status = PaymentOrderStatus.CAPTURE_FAILED,
                retryCount = 3,
                updatedAt = base.updatedAt.plusMinutes(5)
            )
        )
        assertNotNull(updated)
        assertEquals(PaymentOrderStatus.CAPTURE_FAILED, updated!!.status)
        assertEquals(3, updated.retryCount)

        // Attempt to override terminal status CAPTURED -> should remain CAPTURED
        val terminal = paymentOrderMapper.updateReturningIdempotent(
            updated.copy(
                status = PaymentOrderStatus.CAPTURED,
                updatedAt = updated.updatedAt.plusMinutes(5)
            )
        )
        assertNotNull(terminal)
        assertEquals(PaymentOrderStatus.CAPTURED, terminal!!.status)

        val afterTerminal = paymentOrderMapper.updateReturningIdempotent(
            terminal.copy(
                status = PaymentOrderStatus.CAPTURE_FAILED,
                updatedAt = terminal.updatedAt.plusMinutes(10)
            )
        )
        // status stays CAPTURED but retryCount updates via GREATEST
        assertNotNull(afterTerminal)
        assertEquals(PaymentOrderStatus.CAPTURED, afterTerminal!!.status)
        assertEquals(terminal.retryCount, afterTerminal.retryCount)
    }

    @Test
    fun `insertAllIgnore skips duplicates`() {
        upsertPayment(3001L)
        val first = paymentOrderEntity(id = 401L, paymentId = 3001L)
        val second = paymentOrderEntity(id = 402L, paymentId = 3001L)
        val inserts = paymentOrderMapper.insertAllIgnore(listOf(first, second))
        assertEquals(2, inserts)

        val retry = paymentOrderMapper.insertAllIgnore(listOf(first))
        assertEquals(0, retry)
    }

    @Test
    fun `deleteAll clears table`() {
        upsertPayment(4001L)
        paymentOrderMapper.insert(paymentOrderEntity(id = 501L, paymentId = 4001L))
        assertEquals(1, paymentOrderMapper.countAll())

        paymentOrderMapper.deleteAll()
        assertEquals(0, paymentOrderMapper.countAll())
        assertNull(paymentOrderMapper.updateReturningIdempotent(paymentOrderEntity(501L)))
    }
}

