package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.paymentservice.domain.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.PspResultCachePort
import io.micrometer.core.instrument.MeterRegistry
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.MethodArgumentNotValidException
import java.sql.SQLTransientException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderExecutor(
    private val processPspResultUseCase: ProcessPspResultUseCase,
    private val pspClient: PaymentGatewayPort,
    private val meterRegistry: MeterRegistry,
    private val pspResultCache: PspResultCachePort,
    @Qualifier("paymentOrderExecutorPoolConfig") private val pspExecutor: ThreadPoolTaskExecutor
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Single-record listener, no manual ack, no batching, concurrency=1
    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_CREATED],
        containerFactory = "${Topics.PAYMENT_ORDER_CREATED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_CREATED,
        concurrency = "1"
    )
    fun onMessage(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        handle(record) // throw to trigger retries/DLQ; return = commit success
    }

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        val totalStart = System.currentTimeMillis()
        val envelope = record.value()
        val event = envelope.data
        val order = PaymentOrderDomainEventMapper.fromEvent(event)

        logger.info("Processing PaymentOrderCreated, orderId={}", order.publicPaymentOrderId)

        if (order.status != PaymentOrderStatus.INITIATED) {
            logger.info("⏩ Skipping already processed order (status={})", order.status)
            return
        }

        try {
            val key = order.paymentOrderId
            val cached = pspResultCache.get(key)
            val status = if (cached != null) {
                logger.info("♻️ PSP cache hit for {}", key)
                PaymentOrderStatus.valueOf(cached)
            } else {
                val result = safePspCall(order)
                pspResultCache.put(key, result.name)
                result
            }

            val dbStart = System.currentTimeMillis()
            processPspResultUseCase.processPspResult(event = event, pspStatus = status)
            val dbEnd = System.currentTimeMillis()
            logger.info("TIMING: processPspResult took {} ms for {}", (dbEnd - dbStart), key)

            logger.info("TIMING: total handler took {} ms for {}", (System.currentTimeMillis() - totalStart), key)
        } catch (e: Exception) {
            // Throw for anything you want retried or sent to DLQ.
            when (e) {
                is RetriableException,
                is TransientDataAccessException,
                is CannotAcquireLockException,
                is SQLTransientException -> {
                    logger.warn("Retryable exception, will be retried or sent to DLQ", e)
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
                is NonTransientDataAccessException,
                is KafkaException,
                is org.springframework.kafka.KafkaException -> {
                    logger.error("Non-retryable exception, will go to DLQ", e)
                    throw e
                }

                else -> {
                    // Don’t swallow: throwing triggers the DefaultErrorHandler.
                    logger.error("Unexpected error; sending to error handler/DLQ", e)
                    throw e
                }
            }
        }
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        val start = System.currentTimeMillis()
        val future = pspExecutor.submit<PaymentOrderStatus> { pspClient.charge(order) }
        return try {
            future.get(1, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("PSP call timed out for {}", order.paymentOrderId)
            future.cancel(true)
            PaymentOrderStatus.TIMEOUT
        } finally {
            meterRegistry.counter("SafePspCall.total", "status", "success").increment()
            logger.info(
                "TIMING: PSP call took {} ms for {}",
                (System.currentTimeMillis() - start),
                order.paymentOrderId
            )
        }
    }
}