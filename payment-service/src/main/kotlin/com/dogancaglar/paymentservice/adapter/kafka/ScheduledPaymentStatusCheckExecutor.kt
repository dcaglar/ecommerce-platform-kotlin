package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPClient
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Component
class ScheduledPaymentStatusCheckExecutor(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val pspClient: PSPClient,
    private val paymentEventPublisher: PaymentEventPublisher,
    @Qualifier("paymentRetryStatusAdapter") val paymentRetryStatusAdapter:RetryQueuePort
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>) {
        val envelope = record.value()
        val paymentOrderStatusCheck = envelope.data
        val paymentOrder = paymentOrderStatusCheck.toDomain()
        logger.info("Performing PSP status check for ${paymentOrder.paymentOrderId} retry=${paymentOrder.paymentOrderId}")
        try {
            val response: PaymentOrderStatus = safePspCall(paymentOrder,)

            when (response) {
                PaymentOrderStatus.SUCCESSFUL -> {
                    val paidOrder = paymentOrder.markAsPaid().updatedAt(LocalDateTime.now())
                    paymentOrderRepository.save(paidOrder)

                }

                PaymentOrderStatus.PENDING,
                PaymentOrderStatus.CAPTURE_PENDING -> {
                    paymentOrder.markAsPending().incrementRetry().updatedAt(LocalDateTime.now())
                    logger.info("${paymentOrderStatusCheck.paymentOrderId} still pending, re-scheduling")
                    paymentRetryStatusAdapter.scheduleRetry(paymentOrder.paymentOrderId,0)
                }

                else -> {
                    logger.warn("${paymentOrder.paymentOrderId} failed with non-final PSP status: ${paymentOrderStatusCheck.status}")
                    val failed = paymentOrder.markAsFinalizedFailed().incrementRetry()
                    paymentOrderRepository.save(failed)
                }
            }

        } catch (e: Exception) {
            logger.error("Transient error during PSP status check for ${paymentOrder}, rescheduling", e)
            val updatedOrder = paymentOrder.markAsPending().incrementRetry().withRetryReason("transient error").withLastError(e.message)
            //do we want to ssave
            paymentOrderRepository.save(updatedOrder);
            // eeror in status check reschedukle  via redis
            paymentRetryStatusAdapter.scheduleRetry(updatedOrder.paymentOrderId,updatedOrder.retryCount)
        }
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return CompletableFuture.supplyAsync {
            pspClient.checkPaymentStatus(order.paymentOrderId)
        }.get(3, TimeUnit.SECONDS)
        // This should be replaced with your actual PSP integration
    }
}