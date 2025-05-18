package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.delayqueue.JpaDelayQueueAdapter
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component

@Component
class PaymentOrderStatusCheckScheduler(private val delayQueueAdapter: JpaDelayQueueAdapter, val objectMapper: ObjectMapper) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>) {
        val envelope = record.value()
        LogContext.with(envelope) {
            MDC.put(LogFields.TOPIC_NAME, record.topic())
            MDC.put(LogFields.CONSUMER_GROUP, record.topic())
            MDC.put(LogFields.PAYMENT_ORDER_ID, envelope.data.paymentOrderId)
            try {
                val paymentOrderStatusCheckRequestedEvent = envelope.data
                val paymentOrder = paymentOrderStatusCheckRequestedEvent.toDomain()
                val delayMillis = when (val attempt = paymentOrder.retryCount) {
                    1 -> 60_000L  // 1 min
                    2 -> 5 * 60_000L  // 5 min
                    else -> 10 * 60_000L  // 10 min
                }
                logger.info("Received retry request: $envelope")
                delayQueueAdapter.persist(
                    topic = record.topic(),
                    key = record.key(),
                    payload = objectMapper.writeValueAsString(envelope),
                    sendAfterMillis = delayMillis
                )
            } catch (e: Exception){
                logger.error("Unknown execption occured",e);
            } finally {
                MDC.clear()
            }
        }
    }
}