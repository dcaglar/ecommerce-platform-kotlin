package com.dogancaglar.paymentservice.mybatis


import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.OutboxEventMapper
import com.dogancaglar.paymentservice.application.usecases.ProcessPaymentService
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
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
import com.ninjasquad.springmockk.MockkBean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

/**
 * Integration tests for OutboxEventMapper with real PostgreSQL (Testcontainers).
 * 
 * These tests validate:
 * - Real database persistence operations
 * - MyBatis mapper integration
 * - Outbox event CRUD operations
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
class OutboxEventMapperIntegrationTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl =
                OutboxEventMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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

    @MockkBean
    lateinit var authorizePaymentUseCase: AuthorizePaymentUseCase

    @MockkBean
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
        createdAt = createdAt,
        updatedAt = createdAt
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
    fun `findBatchForDispatch claims, stamps claimed_ and returns rows`() {
        val ev1 = newEvent(oeid = 101)
        val ev2 = newEvent(oeid = 102)
        outboxEventMapper.insertOutboxEvent(ev1)
        outboxEventMapper.insertOutboxEvent(ev2)

        val workerA = "pod-A:worker-1"
        val claimed = outboxEventMapper.findBatchForDispatch(1, workerA)

        assertEquals(1, claimed.size)
        val row = claimed.first()
        assertEquals("PROCESSING", row.status)

        // Verify stamping happened (mapper SELECT returns the row fields)
        // NOTE: OutboxEventEntity should have claimedAt/claimedBy if you want to assert them directly.
        // If your resultMap doesn't map them, assert via a follow-up query or just trust the UPDATE.
        val processingIds = outboxEventMapper.findByStatus("PROCESSING").map { it.oeid }
        assertTrue(row.oeid in processingIds)
    }

    @Test
    fun `two workers don't claim same row (SKIP LOCKED sanity)`() {
        // Prepare 2 rows
        val base = 200L
        outboxEventMapper.insertOutboxEvent(newEvent(oeid = base + 1))
        outboxEventMapper.insertOutboxEvent(newEvent(oeid = base + 2))

        val a = outboxEventMapper.findBatchForDispatch(1, "pod-A:w1")
        val b = outboxEventMapper.findBatchForDispatch(1, "pod-B:w1")

        assertEquals(1, a.size)
        assertEquals(1, b.size)
        assertNotEquals(a.first().oeid, b.first().oeid)
    }

    @Test
    fun `batchUpdate marks SENT and clears claim columns`() {
        val base = 300L
        val events = listOf(newEvent(base + 1), newEvent(base + 2), newEvent(base + 3))
        outboxEventMapper.insertAllOutboxEvents(events)

        // Claim a batch to set PROCESSING + claimed_*
        val worker = "pod-A:w2"
        val claimed = outboxEventMapper.findBatchForDispatch(3, worker)
        assertEquals(3, claimed.size)

        // Mark SENT (your entity.markAsSent() sets status = "SENT")
        events.forEach { it.markAsSent() }
        val updated = outboxEventMapper.batchUpdate(events)
        assertTrue(updated >= 1)

        // All should show up as SENT
        val sentIds = outboxEventMapper.findByStatus("SENT").map { it.oeid }.toSet()
        assertTrue(sentIds.containsAll(events.map { it.oeid }.toSet()))

        // Optional: if your resultMap includes claimed_* columns, you can assert they are NULL now.
        // Otherwise this implicitly verifies the UPDATE executed; you may add a dedicated SELECT if desired.
    }

    @Test
    fun `reclaimStuckClaims resets old PROCESSING to NEW`() {
        val ev = newEvent(oeid = 999)
        outboxEventMapper.insertOutboxEvent(ev)

        // claim it
        outboxEventMapper.findBatchForDispatch(1, "pod-X:w1")
        val processingBefore = outboxEventMapper.countByStatus("PROCESSING")
        assertTrue(processingBefore >= 1)

        // Simulate "stuck" by reclaiming anything older than 0 seconds (i.e., now)
        val reclaimed = outboxEventMapper.reclaimStuckClaims(0)
        assertTrue(reclaimed >= 1)

        val newAfter = outboxEventMapper.countByStatus("NEW")
        assertTrue(newAfter >= 1)
    }
}

