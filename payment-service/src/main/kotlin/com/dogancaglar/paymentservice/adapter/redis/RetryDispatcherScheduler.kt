package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.adapter.delayqueue.ScheduledPaymentOrderStatusService
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.event.ScheduledPaymentOrderStatusRequest
import com.dogancaglar.paymentservice.application.helper.PaymentOrderReconstructor
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
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
    @Qualifier("paymentRetryStatusAdapter")
    private val paymentRetryStatusAdapter: RetryQueuePort<ScheduledPaymentOrderStatusRequest>,
    private val paymentOrderRepository: PaymentOrderOutboundPort,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val scheduledPaymentOrderStatusService: ScheduledPaymentOrderStatusService,
    private val reconstructor: PaymentOrderReconstructor
) {

    private val logger = LoggerFactory.getLogger(RetryDispatcherScheduler::class.java)

    @Scheduled(fixedDelay = 5000)
    fun dispatchPaymentOrderRetriesViaRedisQueue() {
        val scheduledPaymentOrderRequestList = paymentRetryPaymentAdapter.pollDueRetries()
        for (envelope in scheduledPaymentOrderRequestList) {
            val order = reconstructor.fromRetryRequestedEvent(envelope.data)
            try {
                if (!shouldSchedule(order)) continue

                paymentEventPublisher.publish(
                    aggregateId = envelope.aggregateId,
                    event = EventMetadatas.PaymentOrderRetryRequestedMetadata,
                    data = envelope.data,
                    parentEventId = envelope.eventId
                )
            } catch (e: Exception) {
                logger.error("Failed to dispatch retry event: ${e.message}", e)
            }
        }
    }


    @Scheduled(fixedDelay = 5000)
    fun dispatchScheduledPaymentOrderStatusRequest() {
        try {
            val duePaymentStatusScheduledEnvelopeList = paymentRetryStatusAdapter.pollDueRetries()
            scheduledPaymentOrderStatusService.persist(duePaymentStatusScheduledEnvelopeList, 60 * 30)

        } catch (e: Exception) {
            logger.error("Failed to save retry duePaymentStatusScheduledEnvelopeList: ${e.message}", e)

        }
    }

    private fun shouldSchedule(order: PaymentOrder): Boolean {
        return order.retryCount <= 5
    }
}