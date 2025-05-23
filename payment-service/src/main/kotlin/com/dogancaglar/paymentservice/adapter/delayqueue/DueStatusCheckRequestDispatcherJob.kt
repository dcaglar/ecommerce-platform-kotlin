package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusScheduled
import com.dogancaglar.paymentservice.domain.event.toDuePaymentOrderStatusCheck
import com.dogancaglar.paymentservice.domain.port.EventPublisherPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant


@Component
class DueStatusCheckRequestDispatcherJob(
    private val repository: ScheduledPaymentOrderRequestRepository,
    private val genericEventPublisher: EventPublisherPort,
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
                    .constructParametricType(EventEnvelope::class.java, PaymentOrderStatusScheduled::class.java)
                //schedule due status check event
                val envelope: EventEnvelope<PaymentOrderStatusScheduled> = objectMapper.readValue(it.payload,envelopeType)
                val dueRequestEvent = envelope.data.toDuePaymentOrderStatusCheck()
                genericEventPublisher.publish(
                    event = EventMetadatas.PaymentOrderStatusCheckExecutorMetadata,
                    aggregateId = envelope.data.paymentOrderId,
                    data = dueRequestEvent,
                    parentEnvelope = envelope
                )
                repository.deleteById(it.id)



            } catch (e: Exception){
                logger.error("${it.payload} error occured $e")

            }
        }
        }
    }