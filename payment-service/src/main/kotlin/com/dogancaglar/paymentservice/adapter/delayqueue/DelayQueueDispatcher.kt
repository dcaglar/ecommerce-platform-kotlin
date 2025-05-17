package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusCheckRequested
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant


@Component
class DelayQueueDispatcher(
    private val repository: DelayedKafkaMessageRepository,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)


    @Transactional
    @Scheduled(fixedRate = 5000)
    fun dispatchDueMessages() {
        logger.info("Pollling due messsage for status check")
        val now = Instant.now()
        val dueMessages = repository.findAllBySendAfterBefore(now)
        dueMessages.forEach {
            try {
                val envelopeType = objectMapper
                    .typeFactory
                    .constructParametricType(EventEnvelope::class.java, PaymentOrderStatusCheckRequested::class.java)
                val envelope: EventEnvelope<PaymentOrderStatusCheckRequested> = objectMapper.readValue(it.payload,envelopeType)


                paymentEventPublisher.publish(
                    event = EventMetadatas.PaymentOrderStatusCheckRequestedMetadata,
                    aggregateId = envelope.data.paymentOrderId,
                    data = envelope.data
                )

                repository.deleteById(it.id)


            } catch (e: Exception){
                logger.error("${it.key} error occured $e")

            }
        }
        }
    }