package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.SpringDataOutboxEventJpaRepository
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import org.springframework.stereotype.Repository

@Repository
class JpaOutboxBufferAdapter(
    private val jpaRepository: SpringDataOutboxEventJpaRepository
) : OutboxEventPort {

    override fun findByStatus(status: String): List<OutboxEvent> =
        jpaRepository.findByStatus(status).map { OutboxEventEntityMapper.toDomain(it) }

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> {
        val entities = events.map { OutboxEventEntityMapper.toEntity(it) }
        return jpaRepository.saveAll(entities).map { OutboxEventEntityMapper.toDomain(it) }
    }

    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = OutboxEventEntityMapper.toEntity(event)
        val savedEntity = jpaRepository.save(entity)
        return OutboxEventEntityMapper.toDomain(savedEntity)
    }
}