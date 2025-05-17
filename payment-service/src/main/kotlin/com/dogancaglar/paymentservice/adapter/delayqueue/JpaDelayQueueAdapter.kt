
package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.delayqueue.mapper.DelayQueueMapper
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.port.DelayQueuePort
import org.springframework.stereotype.Component
import java.time.Duration


@Component
class JpaDelayQueueAdapter(
    private val delayQueueMapper: DelayQueueMapper,
    private val delayedKafkaMessageRepository: DelayedKafkaMessageRepository,
) : DelayQueuePort {


    fun persist(topic: String, key: String, payload: String, sendAfterMillis: Long) {
        val entity = delayQueueMapper.toEntity(
            envelopeJson = payload,
            topic = topic,
            key = key,
            delayMillis = sendAfterMillis
        )
        delayedKafkaMessageRepository.save(entity)
    }

    override fun <T> schedule(eventEnvelope: EventEnvelope<T>, delay: Duration) {
        TODO("Not yet implemented")
    }
}
