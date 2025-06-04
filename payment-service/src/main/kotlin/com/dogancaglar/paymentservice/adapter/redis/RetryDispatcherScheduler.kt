package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RetryDispatcherScheduler(
    private val paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
    private val paymentEventPublisher: PaymentEventPublisher,

    ) {

    private val logger = LoggerFactory.getLogger(RetryDispatcherScheduler::class.java)

    @Scheduled(fixedDelay = 5000)
    fun dispatchPaymentOrderRetriesViaRedisQueue() {
        val expiredPaymentRetryRequestEnvelopeList = paymentRetryQueueAdapter.pollDueRetries()
        for (envelope in expiredPaymentRetryRequestEnvelopeList) {
            try {
                paymentEventPublisher.publish(
                    aggregateId = envelope.aggregateId,
                    eventMetaData = EventMetadatas.PaymentOrderRetryRequestedMetadata,
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