package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.SpringDataOutboxEventJpaRepository
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class JpaOutboxBufferAdapter(
    private val springDataJpaRepository: SpringDataOutboxEventJpaRepository
) : OutboxEventPort {

    override fun findByStatus(status: String): List<OutboxEvent> =
        springDataJpaRepository.findByStatus(status).map { OutboxEventEntityMapper.toDomain(it) }

    override fun findByStatusWithLimit(status: String, limit: Int): List<OutboxEvent> =
        springDataJpaRepository.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, limit))
            .map { OutboxEventEntityMapper.toDomain(it) }

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> {
        val entities = events.map { OutboxEventEntityMapper.toEntity(it) }
        return springDataJpaRepository.saveAll(entities).map { OutboxEventEntityMapper.toDomain(it) }
    }

    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = OutboxEventEntityMapper.toEntity(event)
        val savedEntity = springDataJpaRepository.save(entity)
        return OutboxEventEntityMapper.toDomain(savedEntity)
    }

    override fun countByStatus(status: String): Long {
        return springDataJpaRepository.countByStatus(status)
    }


}