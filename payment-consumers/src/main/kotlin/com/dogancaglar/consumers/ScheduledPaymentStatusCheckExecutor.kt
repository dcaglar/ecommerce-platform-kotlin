package com.dogancaglar.consumers

import com.dogancaglar.application.PaymentOrderStatusCheckRequested
import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.TOPICS
import com.dogancaglar.consumers.base.BaseBatchKafkaConsumer
import com.dogancaglar.payment.application.port.inbound.CreatePaymentUseCase
import com.dogancaglar.payment.application.port.outbound.PaymentGatewayPort
import com.dogancaglar.payment.application.port.outbound.ProcessPspResultUseCase
import com.dogancaglar.payment.domain.factory.PaymentOrderFactory
import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.PaymentOrderStatus
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
    val paymentGatewayPort: PaymentGatewayPort,
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val processPspResultUseCase: ProcessPspResultUseCase,
    val paymetOrderFactory: PaymentOrderFactory,
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
        val order = paymetOrderFactory.fromEvent(paymentOrderStatusCheckRequested)
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

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return externalPspExecutorPoolConig.submit<PaymentOrderStatus> {
            paymentGatewayPort.checkPaymentStatus(order.paymentOrderId.toString())
        }.get(3, TimeUnit.SECONDS)
    }


    override fun filter(envelope: EventEnvelope<PaymentOrderStatusCheckRequested>): Boolean {
        return true
    }


    @KafkaListener(
        topics = [TOPICS.PAYMENT_STATUS_CHECK_SCHEDULER],
        containerFactory = "${TOPICS.PAYMENT_STATUS_CHECK_SCHEDULER}-factory",
        groupId = "${CONSUMER_GROUPS.PAYMENT_STATUS_CHECK_SCHEDULER}",
    )
    fun handleBatchListener(
        records: List<ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>>,
        acknowledgment: Acknowledgment
    ) {
        super.handleBatch(records, acknowledgment)
    }

    override fun consume(record: ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>) {
        TODO("Not yet implemented")
    }

    override fun getExecutor(): ThreadPoolTaskExecutor? {
        return scheduledStatusCheckExecutor
    }


}