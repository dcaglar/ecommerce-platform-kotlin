package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.delayqueue.mapper.ScheduledPaymentOrderRequestMapper
import com.dogancaglar.paymentservice.application.event.ScheduledPaymentOrderStatusRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component


@Component
class ScheduledPaymentOrderStatusService(
    private val scheduledPaymentOrderRequestMapper: ScheduledPaymentOrderRequestMapper,
    private val scheduledPaymentOrderRequestRepository: ScheduledPaymentOrderRequestRepository,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper
) {

    fun updateStatusToExecuted(paymentOrderId: String, newStatus: RequestStatus) {
        scheduledPaymentOrderRequestRepository.updateStatusByIds(paymentOrderId = paymentOrderId, newStatus)
    }

    fun persist(
        scheduledPaymentStatusEnvelopeList: List<EventEnvelope<ScheduledPaymentOrderStatusRequest>>,
        delayMillis: Long
    ) {
        val result = scheduledPaymentStatusEnvelopeList.map { e ->
            scheduledPaymentOrderRequestMapper.toEntity(
                objectMapper.writeValueAsString(e),
                900,
                paymentOrderId = e.aggregateId
            )
        }
        result.stream().map {
            System.out.println(" Timestamps $it.createdAt +  $it.sendAfter")
        }
        scheduledPaymentOrderRequestRepository.saveAll(result)
    }
}
/*
package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.*

interface ScheduledPaymentOrderRequestRepository : JpaRepository<ScheduledPaymentOrderStatusRequestEntity, UUID> {
    fun findAllBySendAfterBefore(now: Instant): List<ScheduledPaymentOrderStatusRequestEntity>
    fun deleteById(queueId : String)


    @Query("""
        SELECT r FROM ScheduledPaymentOrderStatusRequestEntity r
        WHERE r.status = :status AND r.sendAfter <= :now
        ORDER BY r.sendAfter ASC
    """)
    fun findDueRequests(
        @Param("status") status: RequestStatus = RequestStatus.SCHEDULED,
        @Param("now") now: Instant
    ): List<ScheduledPaymentOrderStatusRequestEntity>

    @Modifying
    @Transactional
    @Query("""
        UPDATE ScheduledPaymentOrderStatusRequestEntity r
        SET r.status = :newStatus
        WHERE r.id IN :ids
    """)
    fun updateStatusByIds(
        @Param("ids") ids: List<String>,
        @Param("newStatus") newStatus: RequestStatus
    ): Int
}
 */