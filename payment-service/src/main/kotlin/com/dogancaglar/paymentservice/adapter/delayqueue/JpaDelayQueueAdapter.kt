
package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.delayqueue.mapper.DelayQueueMapper
import com.dogancaglar.paymentservice.adapter.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.kafka.PaymentStatusDelayedQueueExecutor
import com.dogancaglar.paymentservice.domain.port.DelayQueuePort
import org.springframework.stereotype.Component
import java.time.Duration


@Component
class JpaDelayQueueAdapter(
    private  val paymentEventPublisher: PaymentEventPublisher,
    private val delayQueueMapper: DelayQueueMapper,
    private val delayedKafkaMessageRepository: DelayedKafkaMessageRepository,
    val delayedQueueExecutor: PaymentStatusDelayedQueueExecutor
) : DelayQueuePort {

    override fun <T> schedule(eventEnvelope: EventEnvelope<T>, delay: Duration) {
        paymentEventPublisher.publish(
            topic = delayedQueueExecutor.topic,
            aggregateId = eventEnvelope.aggregateId,
            eventType = eventEnvelope.eventType,
            data = eventEnvelope.data
        )
    }

    fun persist(topic: String, key: String, payload: String, sendAfterMillis: Long) {
        val entity = delayQueueMapper.toEntity(
            envelopeJson = payload,
            topic = topic,
            key = key,
            delayMillis = sendAfterMillis
        )
        delayedKafkaMessageRepository.save(entity)
    }
}
