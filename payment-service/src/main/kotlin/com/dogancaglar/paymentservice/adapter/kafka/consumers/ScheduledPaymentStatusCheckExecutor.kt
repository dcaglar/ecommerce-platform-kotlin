package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.kafka.base.BaseBatchKafkaConsumer
import com.dogancaglar.paymentservice.application.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.config.messaging.CONSUMER_GROUPS
import com.dogancaglar.paymentservice.config.messaging.TOPIC_NAMES
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PSPClientPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.*
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.converter.ConversionException
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLTransientException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

//todo  appply sama logic as in retry command executor

@Configuration
class ScheduledExecutorConfig {
    @Bean
    fun scheduledStatusCheckTaskExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 1
        executor.setQueueCapacity(100)
        executor.setThreadNamePrefix("scheduled-status-check-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }
}

@Component
class ScheduledPaymentStatusCheckExecutor(
    val pspClient: PSPClientPort,
    val paymentService: PaymentService,
    @Qualifier("scheduledStatusCheckTaskExecutor") private val scheduledStatusCheckExecutor: ThreadPoolTaskExecutor,
    @Qualifier("externalPspExecutorPoolConfig") private val externalPspExecutorPoolConig: ThreadPoolTaskExecutor
) : BaseBatchKafkaConsumer<PaymentOrderStatusCheckRequested>() {
    private val logger = LoggerFactory.getLogger(javaClass)

    // For higher concurrency, inject a shared executor as a constructor parameter or as a class val.
    // For demo purposes, creating a new executor each time is OK.
    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>) {
        val envelope = record.value()
        val paymentOrderStatusCheckRequested = envelope.data
        val order = paymentService.mapEventToDomain(paymentOrderStatusCheckRequested)

        LogContext.with(
            envelope, mapOf(
                LogFields.TOPIC_NAME to record.topic(),
                LogFields.CONSUMER_GROUP to "payment-status-queue",
                LogFields.PUBLIC_PAYMENT_ID to envelope.data.publicPaymentId,
                LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.data.publicPaymentOrderId,
            )
        ) {
            logger.info("▶️ [Handle Start] Processing PaymentOrderStatusCheckRequested")
            try {
                val response = safePspCall(order)
                logger.info("✅ PSP status returned status=$response for paymentOrderId=${order.paymentOrderId}")
                paymentService.processPspResult(
                    event = paymentOrderStatusCheckRequested,
                    pspStatus = response,
                )
            } catch (e: TimeoutException) {
                logger.error("⏱️ PSP status timed out for orderId=${order.paymentOrderId}, retrying...", e)
                paymentService.processPspResult(
                    event = paymentOrderStatusCheckRequested,
                    pspStatus = PaymentOrderStatus.TIMEOUT
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
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return externalPspExecutorPoolConig.submit<PaymentOrderStatus> {
            pspClient.checkPaymentStatus(order.paymentOrderId.toString())
        }.get(3, TimeUnit.SECONDS)
    }


    override fun filter(envelope: EventEnvelope<PaymentOrderStatusCheckRequested>): Boolean {
        return true
    }


    @KafkaListener(
        topics = [TOPIC_NAMES.PAYMENT_STATUS_CHECK_SCHEDULER],
        containerFactory = "${TOPIC_NAMES.PAYMENT_STATUS_CHECK_SCHEDULER}-factory",
        groupId = "${CONSUMER_GROUPS.PAYMENT_STATUS_CHECK_SCHEDULER}",
    )
    fun handleBatchListener(
        records: List<ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>>,
        acknowledgment: Acknowledgment
    ) {
        //records: List<ConsumerRecord<String, EventEnvelope<T>>>, acknowledgment: Acknowledgment) {
        super.handleBatch(records, acknowledgment)
        acknowledgment.acknowledge()
    }

    override fun consume(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>,
    ) {
        val future = scheduledStatusCheckExecutor.submit {
            handle(record)
        }
        future.get()
    }

}