package com.dogancaglar.paymentservice.adapter.persistence.repository

import com.dogancaglar.paymentservice.adapter.persistence.entity.OutboxEventEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import java.time.LocalDateTime
import java.util.*

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class OutboxEventMapperIntegrationTest @Autowired constructor(
    val outboxEventMapper: OutboxEventMapper
) {
    @Test
    fun `should insert and find outbox event`() {
        val entity = OutboxEventEntity(
            eventId = UUID.randomUUID(),
            eventType = "PAYMENT_CREATED",
            aggregateId = "agg-1",
            payload = "{}",
            status = "NEW",
            createdAt = LocalDateTime.now()
        )
        outboxEventMapper.insert(entity)
        val found = outboxEventMapper.findByStatus("NEW")
        assertThat(found).isNotEmpty
        assertThat(found.first().eventType).isEqualTo("PAYMENT_CREATED")
    }
}
