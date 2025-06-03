package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.event.ScheduledPaymentOrderStatusRequest
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RetryDispatcherScheduler(
    @Qualifier("paymentRetryPaymentAdapter")
    private val paymentRetryPaymentAdapter: RetryQueuePort<PaymentOrderRetryRequested>,
    private val paymentEventPublisher: PaymentEventPublisher,

) {

    private val logger = LoggerFactory.getLogger(RetryDispatcherScheduler::class.java)

    @Scheduled(fixedDelay = 5000)
    fun dispatchPaymentOrderd1RetriesViaRedisQueue() {
        val expiredPaymentRetryRequestEnvelopeList = paymentRetryPaymentAdapter.pollDueRetries()
        for (envelope in expiredPaymentRetryRequestEnvelopeList) {
            try {
                paymentEventPublisher.publish(
                    aggregateId = envelope.aggregateId,
                    event = EventMetadatas.PaymentOrderRetryRequestedMetadata,
                    data = envelope.data,
                    parentEventId = envelope.parentEventId,
                    traceId = envelope.traceId
                )
            } catch (e: Exception) {
                logger.error("Failed to dispatch retry event: ${e.message}", e)
            }
        }
    }
}