package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
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
 * Simple integration test for PaymentOutboundAdapter using real PostgreSQL database.
 * 
 * This test demonstrates:
 * - Real database persistence operations
 * - MyBatis mapper integration
 * - Basic CRUD operations
 */
@MybatisTest
@ContextConfiguration(classes = [InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=true"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis")
class PaymentOutboundAdapterSimpleIntegrationTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl =
                PaymentOutboundAdapterSimpleIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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
            reg.add("spring.liquibase.change-log") { "classpath:db/changelog/changelog.master.xml" }
        }
    }

    @Autowired
    private lateinit var paymentMapper: PaymentMapper

    private lateinit var adapter: PaymentOutboundAdapter

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        adapter = PaymentOutboundAdapter(paymentMapper)
        // Clean up test data
        paymentMapper.deleteAll()
    }

    @Test
    fun `getMaxPaymentId should return 0 when no payments exist`() {
        // When
        val maxId = adapter.getMaxPaymentId()

        // Then
        assertEquals(0L, maxId.value)
    }

    @Test
    fun `save should persist payment to database`() {
        // Given
        val payment = createTestPayment()

        // When
        adapter.save(payment)

        // Then
        val maxId = adapter.getMaxPaymentId()
        assertEquals(1L, maxId.value)
    }

    @Test
    fun `save should handle multiple payments`() {
        // Given
        val payment1 = createTestPayment(id = 1L)
        val payment2 = createTestPayment(id = 2L)
        val payment3 = createTestPayment(id = 3L)

        // When
        adapter.save(payment1)
        adapter.save(payment2)
        adapter.save(payment3)

        // Then
        val maxId = adapter.getMaxPaymentId()
        assertEquals(3L, maxId.value)
    }

    private fun createTestPayment(
        id: Long = 1L,
        status: PaymentStatus = PaymentStatus.INITIATED
    ): Payment {
        return Payment.Builder()
            .paymentId(PaymentId(id))
            .publicPaymentId("pay-$id")
            .buyerId(BuyerId("buyer-$id"))
            .orderId(OrderId("order-$id"))
            .totalAmount(Amount(10000L, "USD"))
            .status(status)
            .createdAt(LocalDateTime.now())
            .paymentOrders(emptyList())
            .build()
    }
}
