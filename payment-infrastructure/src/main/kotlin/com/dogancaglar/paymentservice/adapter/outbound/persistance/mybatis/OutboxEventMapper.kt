package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.OutboxEventEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface OutboxEventMapper {
    fun findByStatus(status: String): List<OutboxEventEntity>
    fun countByStatus(status: String): Long

    // Removed @Select annotation to avoid duplicate mapping with XML
    fun findBatchForDispatch(
        @Param("batchSize") batchSize: Int,
        @Param("workerId") workerId: String
    ): List<OutboxEventEntity>



    fun insertOutboxEvent(event: OutboxEventEntity): Int
    fun insertAllOutboxEvents(events: List<OutboxEventEntity>): Int
    fun updateOutboxEventStatus(
        @Param("oeid") oeid: Long,
        @Param("status") status: String
    ): Int

    fun reclaimStuckClaims(olderThanSeconds: Int): Int

    fun batchUpdate(events: List<OutboxEventEntity>): Int


}