package com.dogancaglar.paymentservice.adapter.persistance

import com.dogancaglar.paymentservice.adapter.persistance.mapper.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventRepository
import org.springframework.stereotype.Repository

@Repository
class JpaOutboxEventAdapter(
    private val jpaRepository: SpringDataOutboxEventJpaRepository
) : OutboxEventRepository {
    override fun findByStatus(status: String): List<OutboxEvent> =
        jpaRepository.findByStatus(status).map { OutboxEventEntityMapper.toDomain(it) }

    override fun saveAll(orders: List<OutboxEvent>) {
        jpaRepository.saveAll(orders.map { OutboxEventEntityMapper.toEntity(it) })
    }

    override fun save(outboxEvent: OutboxEvent) {
        jpaRepository.save(OutboxEventEntityMapper.toEntity(outboxEvent))
    }

}
