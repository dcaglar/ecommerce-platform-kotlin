package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.OutboxEventEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface OutboxEventDispatcherMapper {
    fun findByStatus(status: String): List<OutboxEventEntity>
    fun countByStatus(status: String): Long

    fun findBatchForDispatch(
        @Param("batchSize") batchSize: Int,
        @Param("workerId") workerId: String
    ): List<OutboxEventEntity>

    fun updateOutboxEventStatus(
        @Param("oeid") oeid: Long,
        @Param("status") status: String
    ): Int

    fun unclaimSpecific(params: Map<String, Any>): Int
    fun reclaimStuckClaims(olderThanSeconds: Int): Int

    fun batchUpdate(events: List<OutboxEventEntity>): Int
}
