package com.dogancaglar.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.TOPICS
import com.dogancaglar.consumers.base.BaseBatchKafkaConsumer
import com.dogancaglar.payment.application.mapper.PaymentOrderDomainEventMapper
import com.dogancaglar.payment.application.port.inbound.ProcessPspResultUseCase
import com.dogancaglar.payment.application.port.outbound.PaymentGatewayPort
import com.dogancaglar.payment.application.port.outbound.PspResultCachePort
import com.dogancaglar.payment.domain.PaymentOrderCreated
import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.PaymentOrderStatus
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.convert.ConversionException
import org.springframework.dao.*
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
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
    @Qualifier("paymentOrderExecutorPoolConfig") private val pspExecutor: ThreadPoolTaskExecutor,
    @Qualifier("externalPspExecutorPoolConfig") private val externalPspExecutorPoolConig: ThreadPoolTaskExecutor
) : BaseBatchKafkaConsumer<PaymentOrderCreated>() {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        val totalStart = System.currentTimeMillis()
        val envelope = record.value()
        val event = envelope.data
        val order = PaymentOrderDomainEventMapper.fromEvent(event)
        logger.info("Processing payment order :paymentordercreated")
        if (order.status != PaymentOrderStatus.INITIATED) {
            logger.info("⏩ Skipping already processed order(status=${order.status})")
            return
        }
        val cacheStart = System.currentTimeMillis()
        try {
            val key = order.paymentOrderId
            val cachedResult = pspResultCache.get(key)
            val cacheEnd = System.currentTimeMillis()
            logger.info("TIMING: PSP cache lookup took ${cacheEnd - cacheStart} ms for $key")
            val pspStart = System.currentTimeMillis()
            val status = if (cachedResult != null) {
                logger.info("♻️ Cache hit for $key → $cachedResult")
                PaymentOrderStatus.valueOf(cachedResult)
            } else {
                val result = safePspCall(order)
                pspResultCache.put(key, result.name)
                logger.info("✅ PSP returned $result for $key")
                result
            }
            val pspEnd = System.currentTimeMillis()
            logger.info("TIMING: PSP call (including cache, if miss) took ${pspEnd - pspStart} ms for $key")
            val dbStart = System.currentTimeMillis()
            processPspResultUseCase.processPspResult(event = event, pspStatus = status)
            val dbEnd = System.currentTimeMillis()
            logger.info("TIMING: processPspResult (DB/write) took  ${dbEnd - dbStart} ms for $key")
            val totalEnd = System.currentTimeMillis()
            logger.info("TIMING: Total handler time: ${totalEnd - totalStart} ms for $key")
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
        val pspCallStart = System.currentTimeMillis()
        val future = externalPspExecutorPoolConig.submit<PaymentOrderStatus> { pspClient.charge(order) }
        return try {
            future.get(1, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("PSP call timed out for paymentOrderId=${order.paymentOrderId}, scheduling retry")
            future.cancel(true) // Attempt to interrupt the task if it times out
            return PaymentOrderStatus.TIMEOUT
        } finally {
            meterRegistry.counter("SafePspCall.total", "status", "success").increment()
            val pspCallEnd = System.currentTimeMillis()
            logger.info("TIMING: Real PSP call took \\${pspCallEnd - pspCallStart} ms for paymentOrderId=\\${order.paymentOrderId}")
            logger.info("TIMING: Real PSP call took ${pspCallEnd - pspCallStart} ms for paymentOrderId=${order.paymentOrderId}")
        }
    }


    override fun consume(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>,
    ) {
        handle(record)
    }

    @Override
    override fun getExecutor(): ThreadPoolTaskExecutor? {
        return pspExecutor
    }


    @KafkaListener(
        topics = [TOPICS.PAYMENT_ORDER_CREATED],
        containerFactory = "${TOPICS.PAYMENT_ORDER_CREATED}-factory",
        groupId = "${CONSUMER_GROUPS.PAYMENT_ORDER_CREATED}",
        concurrency = "1"
    )

    fun handleBatchListener(
        records: List<ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>>,
        acknowledgment: Acknowledgment
    ) {
        super.handleBatch(records, acknowledgment)
    }


    override fun filter(envelope: EventEnvelope<PaymentOrderCreated>): Boolean {
        return true
    }
}
/*
@Component
class PaymentOrderExecutor(
    private val createPsp: U
    private val paymentOrderFactory: PaymentOrderFactory,
    private val pspClient: PaymentGatewayPort,
    private val meterRegistry: MeterRegistry,
    private val pspResultCache: PspResultCachePort,
    @Qualifier("paymentOrderExecutorPoolConfig") private val pspExecutor: ThreadPoolTaskExecutor,
    @Qualifier("externalPspExecutorPoolConfig") private val externalPspExecutorPoolConig: ThreadPoolTaskExecutor
) : BaseBatchKafkaConsumer<PaymentOrderCreated>() {
    private val logger = LoggerFactory.getLogger(javaClass)




}
        */
