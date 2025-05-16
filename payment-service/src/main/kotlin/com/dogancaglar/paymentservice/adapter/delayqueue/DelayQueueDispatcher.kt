package com.dogancaglar.paymentservice.adapter.delayqueue

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
            kafkaTemplate.send(it.topic, it.key, it.payload)
            repository.deleteById(it.id)
        }
    }
}