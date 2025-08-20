package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.math.BigDecimal
import java.time.LocalDateTime

@MybatisTest
@ContextConfiguration(classes = [InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=false"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis")
class PaymentOrderMapperTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl =
                PaymentOrderMapperTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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
    fun `terminal SUCCESSFUL sticks against later PENDING update`() {
        val paymentOrderId = PaymentOrderId(2L)
        val paymentId = PaymentId(200L)
        val sellerId = SellerId("seller-abc")
        val now = LocalDateTime.now()

        // Insert INITIATED
        val init = PaymentOrder.createNew(
            paymentOrderId = paymentOrderId,
            publicPaymentOrderId = "PO-0002",
            paymentId = paymentId,
            publicPaymentId = "PAY-200",
            sellerId = sellerId,
            amount = Amount(currency = "USD", value = BigDecimal.valueOf(20200)),
            createdAt = now.minusDays(1)
        )
        paymentOrderMapper.insertAllIgnore(listOf(PaymentOrderEntityMapper.toEntity(init)))

        // Mark as SUCCESSFUL and update
        val successful = init.markAsPaid().withUpdatedAt(now.minusMinutes(5))
        paymentOrderMapper.updateReturningIdempotent(PaymentOrderEntityMapper.toEntity(successful))

        // Try to update PENDING after SUCCESSFUL
        val pending = successful.markAsPending().withUpdatedAt(now)
        paymentOrderMapper.updateReturningIdempotent(PaymentOrderEntityMapper.toEntity(pending))

        // Fetch and assert status remains SUCCESSFUL
        val persisted = paymentOrderMapper.findByPaymentOrderId(paymentOrderId.value).first()
        assertEquals(PaymentOrderStatus.SUCCESSFUL, persisted.status)
    }

    @Test
    fun `non-terminal INITIATED can move to PENDING`() {
        val paymentOrderId = PaymentOrderId(3L)
        val paymentId = PaymentId(300L)
        val sellerId = SellerId("seller-xyz")
        val now = LocalDateTime.now()

        val init = PaymentOrder.createNew(
            paymentOrderId = paymentOrderId,
            publicPaymentOrderId = "PO-0003",
            paymentId = paymentId,
            publicPaymentId = "PAY-300",
            sellerId = sellerId,
            amount = Amount(currency = "USD", value = BigDecimal.valueOf(30300)),
            createdAt = now.minusDays(1)
        )
        paymentOrderMapper.insertAllIgnore(listOf(PaymentOrderEntityMapper.toEntity(init)))

        val pending = init.markAsPending().withUpdatedAt(now)
        paymentOrderMapper.updateReturningIdempotent(PaymentOrderEntityMapper.toEntity(pending))

        val row = paymentOrderMapper.findByPaymentOrderId(paymentOrderId.value).first()
        assertEquals(PaymentOrderStatus.PENDING, row.status)
        // updated_at should be >= init.updated_at; exact value depends on DB function, so we don’t assert equality here
    }
}