package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.adapter.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.domain.event.mapper.toRetryEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RetryDispatcherScheduler(
    @Qualifier("paymentRetryQueue")
    private val paymentRetryQueue: RetryQueuePort,
    @Qualifier("paymentStatusQueue")
    private val  paymentStatusQueue: RetryQueuePort,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(RetryDispatcherScheduler::class.java)

    @Scheduled(fixedDelay = 5000)
    fun dispatchDueRetries() {
        val dueOrderIds = paymentRetryQueue.pollDueRetries()
        val dueOrderStatusIds = paymentStatusQueue.pollDueRetries()
        for (paymentOrderId in dueOrderIds) {
            val order = paymentOrderRepository.findById(paymentOrderId)
            if (!shouldRetry(order!!)) continue

            val paymentOrderRetryEvent = order.toRetryEvent()
            paymentEventPublisher.publish(
                topic = "payment_order_retry",
                aggregateId = paymentOrderId,
                eventType = "payment_order_retry",
                data = paymentOrderRetryEvent
            )
        }

        for (paymentOrderId in dueOrderStatusIds) {
            val order = paymentOrderRepository.findById(paymentOrderId)
            if (!shouldRetry(order!!)) continue

            val paymentOrderRetryEvent = order.toRetryEvent()
            paymentEventPublisher.publish(
                topic = "payment_order_retry",
                aggregateId = paymentOrderId,
                eventType = "payment_order_retry",
                data = paymentOrderRetryEvent
            )
        }
    }

    private fun shouldRetry(order: PaymentOrder): Boolean {
        return order.retryCount <= 5
    }
}