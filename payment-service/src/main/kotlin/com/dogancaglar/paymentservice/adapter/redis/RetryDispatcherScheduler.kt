package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.adapter.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.domain.event.mapper.toRetryEvent
import com.dogancaglar.paymentservice.domain.event.toRetryStatusEvent
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
    @Qualifier("paymentRetryAdapter")
    private val paymentRetryQueue: RetryQueuePort,
    @Qualifier("paymentRetryStatusAdapter")
    private val  paymentStatusQueue: RetryQueuePort,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val paymentEventPublisher: PaymentEventPublisher,
    @Qualifier("myObjectMapper")
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
            logger.info("Will deserialize the ${objectMapper.writeValueAsString(order)}")
            val paymentOrderRetryEvent = order.toRetryEvent()
            paymentEventPublisher.publish(
                aggregateId = paymentOrderId,
                event = EventMetadatas.PaymentOrderRetryRequestedMetadata,
                data = paymentOrderRetryEvent
            )
        }

        for (paymentOrderId in dueOrderStatusIds) {
            val order = paymentOrderRepository.findById(paymentOrderId)
            if (!shouldRetry(order!!)) continue

            val paymentOrderStatusCheckRequested = order.toRetryStatusEvent()
            paymentEventPublisher.publish(
                aggregateId = paymentOrderId,
                event = EventMetadatas.PaymentOrderStatusCheckRequestedMetadata,
                data = paymentOrderStatusCheckRequested
            )
        }
    }

    private fun shouldRetry(order: PaymentOrder): Boolean {
        return order.retryCount <= 5
    }
}