package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentIntentEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Configuration
import java.sql.DriverManager
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.Contexts
import liquibase.LabelExpression

@Tag("integration")
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
@Testcontainers
class PaymentIntentMapperIntegrationTest {

    @Configuration
    @MapperScan("com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper")
    class TestConfig


    @Autowired
    private lateinit var paymentIntentMapper: PaymentIntentMapper

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("testdb")
            withUsername("testuser")
            withPassword("testpass")
        }

        init {
            postgres.start()
            
            val connection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            val liquibase = Liquibase("db/changelog/changelog.master.xml", ClassLoaderResourceAccessor(), database)
            liquibase.update(Contexts(), LabelExpression())
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerPgProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName)
        }
    }

    private fun createTestEntity(id: Long, status: String = "CREATED_PENDING", pspRef: String? = null): PaymentIntentEntity {
        return PaymentIntentEntity(
            paymentIntentId = id,
            pspReference = pspRef,
            buyerId = "buyer-123",
            orderId = "order-456",
            totalAmountValue = 1000L,
            currency = "USD",
            status = status,
            createdAt = Utc.nowInstant(),
            updatedAt = Utc.nowInstant(),
            paymentLinesJson = "[]"
        )
    }

    @Test
    fun `should insert and find payment intent`() {
        val entity = createTestEntity(1001L)
        val rows = paymentIntentMapper.insert(entity)
        assertEquals(1, rows)

        val fetched = paymentIntentMapper.findById(1001L)
        assertNotNull(fetched)
        assertEquals(1001L, fetched?.paymentIntentId)
        assertEquals("buyer-123", fetched?.buyerId)
        assertEquals("CREATED_PENDING", fetched?.status)
    }

    @Test
    fun `should update payment intent`() {
        val entity = createTestEntity(1002L)
        val rows = paymentIntentMapper.insert(entity)
        assertEquals(1, rows)
        val fetchedInserted = paymentIntentMapper.findById(1002L)
        assertEquals("CREATED_PENDING", fetchedInserted?.status)
        val updatedEntity = entity.copy(status = "CREATED", updatedAt = Utc.nowInstant())
        val updatedRows = paymentIntentMapper.update(updatedEntity)
        assertEquals(1, updatedRows)
        val fetchedUpdated = paymentIntentMapper.findById(1002L)
        assertEquals("CREATED", fetchedUpdated?.status)
    }

    @Test
    fun `should mark pending auth if status is CREATED`() {
        val entity = createTestEntity(1003L, status = "CREATED")
        paymentIntentMapper.insert(entity)

        val rows = paymentIntentMapper.tryMarkPendingAuth(1003L, Utc.nowInstant())
        assertEquals(1, rows)

        val fetched = paymentIntentMapper.findById(1003L)
        assertEquals("PENDING_AUTH", fetched?.status)
    }

    @Test
    fun `should NOT mark pending auth if status is not CREATED`() {
        val entity = createTestEntity(1004L, status = "AUTHORIZED")
        paymentIntentMapper.insert(entity)

        val rows = paymentIntentMapper.tryMarkPendingAuth(1004L, Utc.nowInstant())
        assertEquals(0, rows)

        val fetched = paymentIntentMapper.findById(1004L)
        assertEquals("AUTHORIZED", fetched?.status)
    }

    @Test
    fun `should update psp reference`() {
        val entity = createTestEntity(1005L, pspRef = null)
        paymentIntentMapper.insert(entity)

        val rows = paymentIntentMapper.updatePspReference(1005L, "psp-ref-123", Utc.nowInstant())
        assertEquals(1, rows)

        val fetched = paymentIntentMapper.findById(1005L)
        assertEquals("psp-ref-123", fetched?.pspReference)
    }

    @Test
    fun `should get max payment intent id`() {
        paymentIntentMapper.insert(createTestEntity(2001L))
        paymentIntentMapper.insert(createTestEntity(2005L))
        paymentIntentMapper.insert(createTestEntity(2003L))

        val maxId = paymentIntentMapper.getMaxPaymentIntentId()
        assertTrue(maxId!! >= 2005L) // there might be other tests inserting stuff
    }

    @Test
    fun `should delete by id`() {
        val entity = createTestEntity(1006L)
        paymentIntentMapper.insert(entity)

        val rows = paymentIntentMapper.deleteById(1006L)
        assertEquals(1, rows)

        val fetched = paymentIntentMapper.findById(1006L)
        assertNull(fetched)
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `should handle concurrent tryMarkPendingAuth correctly`() {
        val paymentIntentId = 1007L
        val entity = createTestEntity(paymentIntentId, status = "CREATED")
        paymentIntentMapper.insert(entity)

        val threadCount = 5
        val latch = java.util.concurrent.CountDownLatch(threadCount)
        val startLatch = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)

        val successfulUpdates = java.util.concurrent.atomic.AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await() // Wait for the green light to maximize concurrency
                    val rowsUpdated = paymentIntentMapper.tryMarkPendingAuth(paymentIntentId, Utc.nowInstant())
                    if (rowsUpdated == 1) {
                        successfulUpdates.incrementAndGet()
                    }
                } catch (e: Exception) {
                    // ignore
                } finally {
                    latch.countDown()
                }
            }
        }

        // Give threads a moment to reach startLatch.await()
        Thread.sleep(100)
        // Fire them all at once
        startLatch.countDown()

        latch.await()
        executor.shutdown()

        // Only exactly 1 thread should have succeeded in updating the row
        assertEquals(1, successfulUpdates.get(), "Only one thread should succeed in marking as PENDING_AUTH")

        val fetched = paymentIntentMapper.findById(paymentIntentId)
        assertEquals("PENDING_AUTH", fetched?.status)
    }
}
