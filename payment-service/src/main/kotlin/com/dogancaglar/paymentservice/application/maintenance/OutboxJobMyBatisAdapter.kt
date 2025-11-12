package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.paymentservice.domain.event.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventPort
import org.mybatis.spring.SqlSessionTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository

@Repository("outboxJobPort")
class OutboxJobMyBatisAdapter(
    @Qualifier("outboxSqlSessionTemplate")
    private val template: SqlSessionTemplate
) : OutboxEventPort {

    private val mapper = template.getMapper(
        com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.OutboxEventMapper::class.java
    )


  override fun countByStatus(status: String): Long = mapper.countByStatus(status)

  override fun findBatchForDispatch(batchSize: Int,workerId: String) =
    mapper.findBatchForDispatch(batchSize, workerId = workerId)
      .map(com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.OutboxEventEntityMapper::toDomain)


    override fun updateAll(events: List<OutboxEvent>) {
    if (events.isNotEmpty())
      mapper.batchUpdate(events.map(
        com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.OutboxEventEntityMapper::toEntity
      ))
  }

    fun unclaimSpecific(workerId: String, oeids: List<Long>): Int {
        if (oeids.isEmpty()) return 0
        val params = mapOf(
            "workerId" to workerId,
            "oeids" to oeids
        )
        return mapper.unclaimSpecific(params)
    }

    fun reclaimStuckClaims(olderThanSeconds: Int): Int =
        mapper.reclaimStuckClaims(olderThanSeconds)

  // not needed in job:
  override fun saveAll(events: List<OutboxEvent>) = error("Unsupported here")
  override fun save(event: OutboxEvent) = error("Unsupported here")
  override fun findByStatus(status: String) = error("Unsupported here")
}