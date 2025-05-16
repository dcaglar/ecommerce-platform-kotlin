package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusCheckRequested
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DelayQueueDispatcher(
    private val repository: DelayedKafkaMessageRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    @Scheduled(fixedRate = 5000)
    fun dispatchDueMessages() {
        val now = Instant.now()
        val dueMessages = repository.findAllBySendAfterBefore(now)
        dueMessages.forEach {
            it.payload
            kafkaTemplate.send(it.topic, it.key, it.payload)
            repository.deleteById(it.id)
        }
    }
}