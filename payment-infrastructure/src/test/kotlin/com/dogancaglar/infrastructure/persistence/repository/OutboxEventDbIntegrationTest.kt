package com.dogancaglar.infrastructure.persistence.repository


import com.dogancaglar.infrastructure.persistence.entity.OutboxEventEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

@SpringBootTest(classes = [TestConfig::class])
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxEventMapperIntegrationTest {

    @BeforeAll
    fun initSchema() {
        val ddl = javaClass.classLoader.getResource("schema-test.sql")!!.readText()
        postgres.createConnection("").use { c -> c.createStatement().execute(ddl) }
    }

    companion object {
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
        outboxEventMapper.insert(ev)

        val foundIds = outboxEventMapper.findByStatus("NEW").map { it.oeid }
        assertTrue(ev.oeid in foundIds)
    }

    @Test
    fun `countByStatus works`() {
        val ev = newEvent()
        outboxEventMapper.insert(ev)

        val count = outboxEventMapper.countByStatus("NEW")
        assertTrue(count >= 1)
    }

    @Test
    fun `findBatchForDispatch claims and returns correct events`() {
        val ev1 = newEvent(oeid = 1)
        val ev2 = newEvent(oeid = 2)
        outboxEventMapper.insert(ev1)
        outboxEventMapper.insert(ev2)

        val claimed = outboxEventMapper.findBatchForDispatch(1)

        assertEquals(1, claimed.size)
        assertEquals("PROCESSING", claimed.first().status)
    }

    @Test
    fun `batchUpsert inserts then updates`() {
        // insert three fresh rows
        val base = 3L
        val events = listOf(
            newEvent(base + 1),
            newEvent(base + 2),
            newEvent(base + 3)
        )

        val insertedRows = outboxEventMapper.batchUpsert(events)
        assertEquals(3, insertedRows)

        /* verify they were NEW */
        events.forEach {
            assertEquals(
                "NEW",
                outboxEventMapper.findByStatus("NEW")
                    .first { ev -> ev.oeid == it.oeid }.status
            )
        }

        /* update them all to SENT */
        events.forEach { it.markAsSent() }
        val updatedRows = outboxEventMapper.batchUpsert(events)
        assertEquals(3, updatedRows)

        val sentIds =
            outboxEventMapper.findByStatus("SENT").map { it.oeid }.toSet()
        // expect only the rows we touched
        assertEquals(events.map { it.oeid }.toSet(), sentIds)
    }

}