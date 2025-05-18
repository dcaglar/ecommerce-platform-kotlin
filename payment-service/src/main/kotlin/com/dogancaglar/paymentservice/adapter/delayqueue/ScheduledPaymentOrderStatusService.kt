
package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.delayqueue.mapper.ScheduledPaymentOrderRequestMapper
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusScheduled
import com.dogancaglar.paymentservice.domain.event.ScheduledPaymentOrderStatusRequest
import com.dogancaglar.paymentservice.domain.port.DelayQueuePort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Duration


@Component
class ScheduledPaymentOrderStatusService(
    private val scheduledPaymentOrderRequestMapper: ScheduledPaymentOrderRequestMapper,
    private val scheduledPaymentOrderRequestRepository: ScheduledPaymentOrderRequestRepository,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper
)  {


    fun persist(scheduledPaymentStatusEnvelopeList: List<EventEnvelope<ScheduledPaymentOrderStatusRequest>>, delayMillis: Long) {

        val result = scheduledPaymentStatusEnvelopeList.map { e -> scheduledPaymentOrderRequestMapper.toEntity(objectMapper.writeValueAsString(e),1800) }

        scheduledPaymentOrderRequestRepository.saveAll(result)
    }
}
