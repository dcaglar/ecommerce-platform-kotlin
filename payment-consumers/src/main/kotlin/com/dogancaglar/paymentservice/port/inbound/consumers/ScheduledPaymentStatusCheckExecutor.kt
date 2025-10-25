package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.paymentservice.domain.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentGatewayPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.*
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.converter.ConversionException
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.sql.SQLTransientException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


@Component
class ScheduledPaymentStatusCheckExecutor(
    val paymentGatewayPort: PaymentGatewayPort,
    private val processPspResultUseCase: ProcessPspResultUseCase,
    @Qualifier("paymentStatusPspPool") private val externalPspExecutorPoolConig: ThreadPoolTaskExecutor,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_STATUS_CHECK],
        containerFactory = "${Topics.PAYMENT_STATUS_CHECK}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_STATUS_CHECK_SCHEDULER
    )
    fun onMessage(record: ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>) {
        handle(record) // throw to trigger retries/DLQ; return = commit success
    }


    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>) {
        val envelope = record.value()
        val paymentOrderStatusCheckRequested = envelope.data
        val order = paymentOrderDomainEventMapper.fromEvent(paymentOrderStatusCheckRequested)
        logger.info("▶️ [Handle Start] Processing PaymentOrderStatusCheckRequested")
        try {
            val response = safePspCall(order)
            logger.info("✅ PSP status returned status=$response for paymentOrderId=${order.paymentOrderId}")
            processPspResultUseCase.processPspResult(
                event = paymentOrderStatusCheckRequested,
                pspStatus = response,
            )
        } catch (e: TimeoutException) {
            logger.error("⏱️ PSP status timed out for orderId=${order.paymentOrderId}, retrying...", e)
            processPspResultUseCase.processPspResult(
                event = paymentOrderStatusCheckRequested,
                pspStatus = PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
            )
        } catch (e: Exception) {
            when (e) {
                is RetriableException,
                is TransientDataAccessException,
                is CannotAcquireLockException,
                is SQLTransientException -> {
                    logger.warn("Retryable exception occurred, will be retried or sent to DLQ", e)
                    throw e
                }

                is IllegalArgumentException,
                is NullPointerException,
                is ClassCastException,
                is ConversionException,
                is DeserializationException,
                is SerializationException,
                is MethodArgumentNotValidException,
                is DuplicateKeyException,
                is DataIntegrityViolationException,
                is KafkaException,
                is org.springframework.kafka.KafkaException,
                is NonTransientDataAccessException -> {
                    logger.error("Non-retryable exception occurred", e)
                    throw e
                }

                else -> {
                    logger.error("unexpected error occurred while processing record ${record.value().eventId}", e)
                }
            }
        }
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        val start = System.currentTimeMillis()
        val future = externalPspExecutorPoolConig.submit<PaymentOrderStatus> { paymentGatewayPort.charge(order) }
        return try {
            future.get(1, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("PSP call timed out for {}", order.paymentOrderId)
            future.cancel(true)
            PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        } finally {
            logger.info(
                "TIMING: PSP call took {} ms for {}",
                (System.currentTimeMillis() - start),
                order.paymentOrderId
            )
        }
    }


}