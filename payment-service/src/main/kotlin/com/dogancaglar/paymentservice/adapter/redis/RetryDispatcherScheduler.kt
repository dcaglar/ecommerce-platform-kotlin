package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.adapter.delayqueue.ScheduledPaymentOrderStatusService
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.event.ScheduledPaymentOrderStatusRequest
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
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
    private val  paymentRetryStatusAdapter: RetryQueuePort<ScheduledPaymentOrderStatusRequest>,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val scheduledPaymentOrderStatusService: ScheduledPaymentOrderStatusService
) {

    private val logger = LoggerFactory.getLogger(RetryDispatcherScheduler::class.java)

    @Scheduled(fixedDelay = 5000)
    fun dispatchPaymentOrderRetriesViaRedisQueue() {
        val scheduledPaymentOrderRequestList = paymentRetryPaymentAdapter.pollDueRetries()
        for (envelope in scheduledPaymentOrderRequestList) {
            try {
                if (!shouldSchedule(envelope.data.toDomain())) continue

                paymentEventPublisher.publish(
                    aggregateId = envelope.aggregateId,
                    event = EventMetadatas.PaymentOrderRetryRequestedMetadata,
                    data = envelope.data,
                    parentEnvelope = envelope
                )
            } catch (e: Exception) {
                logger.error("Failed to dispatch retry event: ${e.message}", e)
            }
        }
    }


    @Scheduled(fixedDelay = 5000)
    fun dispatchScheduledPaymentOrderStatusRequest() {
        val duePaymentStatusScheduledEnvelopeList = paymentRetryStatusAdapter.pollDueRetries()
        scheduledPaymentOrderStatusService.persist(duePaymentStatusScheduledEnvelopeList,60*30)

    }

    private fun shouldSchedule(order: PaymentOrder): Boolean {
        return order.retryCount <= 5
    }
}