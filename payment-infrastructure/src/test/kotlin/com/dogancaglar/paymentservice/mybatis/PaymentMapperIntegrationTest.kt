package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentMapper
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
import java.time.LocalDateTime

@Tag("integration")
@MybatisTest
@ContextConfiguration(classes = [InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=false"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis")
class PaymentMapperIntegrationTest {

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
                PaymentMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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

    private fun sampleEntity(id: Long = 101L, key: String = "key-$id") =
        PaymentEntity(
            paymentId = id,
            buyerId = "buyer-$id",
            orderId = "order-$id",
            totalAmountValue = 10_000,
            capturedAmountValue = 0,
            idempotencyKey = key,
            currency = "USD",
            status = "PENDING_AUTH",
            createdAt = LocalDateTime.now().withNano(0),
            updatedAt = LocalDateTime.now().withNano(0)
        )

    @Test
    fun `insert find update and delete payment`() {
        val createdAt = LocalDateTime.now().withNano(0)
        val entity = PaymentEntity(
            paymentId = 101L,
            buyerId = "buyer-1",
            orderId = "order-1",
            totalAmountValue = 10_000,
            capturedAmountValue = 0,
            idempotencyKey = "12345",
            currency = "USD",
            status = "PENDING_AUTH",
            createdAt = createdAt,
            updatedAt = createdAt
        )

        val inserted = paymentMapper.insertIgnore(entity)
        assertEquals(1, inserted)

        val fetched = paymentMapper.findById(101L)
        assertEquals(entity, fetched)

        assertEquals(101L, paymentMapper.getMaxPaymentId())

        val updatedAt = createdAt.plusMinutes(5)
        val updatedEntity = entity.copy(
            capturedAmountValue = 5_000,
            status = "CAPTURED_PARTIALLY",
            updatedAt = updatedAt
        )
        val updatedCount = paymentMapper.update(updatedEntity)
        assertEquals(1, updatedCount)

        val refetched = paymentMapper.findById(101L)
        assertEquals(updatedEntity, refetched)

        val deleted = paymentMapper.deleteById(101L)
        assertEquals(1, deleted)

        assertNull(paymentMapper.findById(101L))
    }
    @Test
    fun `insert duplicate idempotency key should be ignored`() {
        val entity1 = sampleEntity(10L, "same-key")
        val entity2 = sampleEntity(11L, "same-key")

        val firstInsert = paymentMapper.insertIgnore(entity1)
        assertEquals(1, firstInsert)

        val secondInsert = paymentMapper.insertIgnore(entity2)
        assertEquals(0, secondInsert, "ON CONFLICT DO NOTHING should skip duplicate")

        val fetched = paymentMapper.findByIdempotencyKey("same-key")
        assertNotNull(fetched)
        assertEquals(entity1.paymentId, fetched!!.paymentId)
    }

    @Test
    fun `findByIdempotencyKey should return correct row`() {
        val entity = sampleEntity(200L, "unique-key")
        paymentMapper.insertIgnore(entity)
        val found = paymentMapper.findByIdempotencyKey("unique-key")
        assertNotNull(found)
        assertEquals(entity.buyerId, found!!.buyerId)
    }

    @Test
    fun `getMaxPaymentId should return 0 when table empty`() {
        val max = paymentMapper.getMaxPaymentId()
        assertEquals(0L, max)
    }

    @Test
    fun `idempotent insert then update does not break unique constraint`() {
        val key = "idem-test"
        val entity = sampleEntity(333L, key)
        paymentMapper.insertIgnore(entity)
        // inserting again with same key but new id should no-op
        val again = paymentMapper.insertIgnore(entity.copy(paymentId = 334L))
        assertEquals(0, again)
        // update still works on existing record
        paymentMapper.update(entity.copy(status = "AUTHORIZED"))
        val fetched = paymentMapper.findByIdempotencyKey(key)
        assertEquals("AUTHORIZED", fetched!!.status)
    }
}
