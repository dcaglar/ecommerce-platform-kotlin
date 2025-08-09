package com.dogancaglar.mybatis


import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.OutboxEventMapper
import com.dogancaglar.paymentservice.application.usecases.ProcessPaymentService
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mybatis.spring.annotation.MapperScan
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

@MybatisTest
@ContextConfiguration(classes = [com.dogancaglar.paymentservice.InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=false"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis") // ← add this
class OutboxEventMapperTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl =
                OutboxEventMapperTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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
    lateinit var outboxEventMapper: OutboxEventMapper

    @MockitoBean
    lateinit var createPaymentService: CreatePaymentUseCase

    @MockitoBean
    lateinit var processPaymentService: ProcessPaymentService


    @Autowired
    lateinit var ctx: ApplicationContext

    @Test
    fun debugBeans() {
        println("Beans loaded in test context:")
        ctx.beanDefinitionNames.sorted().forEach { println(it) }
    }

    private fun newEvent(
        oeid: Long = System.currentTimeMillis(),
        status: String = "NEW",
        createdAt: LocalDateTime = LocalDateTime.now()
    ) = OutboxEventEntity(
        oeid = oeid,
        eventType = "PAYMENT_ORDER_CREATED",
        aggregateId = "agg-1",
        payload = "{\"foo\": \"bar\"}",
        status = status,
        createdAt = createdAt
    )


    // ───────────────────── tests ──────────────────────
    @Test
    fun `insert and findByStatus works`() {
        val ev = newEvent()
        outboxEventMapper.insertOutboxEvent(ev)

        val foundIds = outboxEventMapper.findByStatus("NEW").map { it.oeid }
        assertTrue(ev.oeid in foundIds)
    }

    @Test
    fun `countByStatus works`() {
        val ev = newEvent()
        outboxEventMapper.insertOutboxEvent(ev)

        val count = outboxEventMapper.countByStatus("NEW")
        assertTrue(count >= 1)
    }

    @Test
    fun `findBatchForDispatch claims and returns correct events`() {
        val ev1 = newEvent(oeid = 1)
        val ev2 = newEvent(oeid = 2)
        outboxEventMapper.insertOutboxEvent(ev1)
        outboxEventMapper.insertOutboxEvent(ev2)

        val claimed = outboxEventMapper.findBatchForDispatch(1)

        assertEquals(1, claimed.size)
        assertEquals("PROCESSING", claimed.first().status)
    }

    @Test
    fun `batchUpsert inserts then updates`() {
        val base = 3L
        val events = listOf(
            newEvent(base + 1),
            newEvent(base + 2),
            newEvent(base + 3)
        )

        // Insert
        val insertedRows = outboxEventMapper.insertAllOutboxEvents(events)
        assertTrue(insertedRows >= 1) // MyBatis returns last stmt count; don't rely on it

        // Verify the 3 rows are actually there (status NEW)
        val newIds = outboxEventMapper.findByStatus("NEW").map { it.oeid }.toSet()
        assertTrue(newIds.containsAll(events.map { it.oeid }.toSet()))

        // Update to SENT
        events.forEach { it.markAsSent() }
        val updatedRows = outboxEventMapper.batchUpdate(events)
        assertTrue(updatedRows >= 1) // same reasoning as insert

        // Verify the 3 rows are now SENT
        val sentIds = outboxEventMapper.findByStatus("SENT").map { it.oeid }.toSet()
        assertTrue(sentIds.containsAll(events.map { it.oeid }.toSet()))
    }

}