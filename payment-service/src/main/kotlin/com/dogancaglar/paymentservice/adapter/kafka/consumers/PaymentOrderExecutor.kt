package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.kafka.base.BaseSingleKafkaConsumer
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PSPClientPort
import com.dogancaglar.paymentservice.domain.port.PspResultCachePort
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.errors.RetriableException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.TransientDataAccessException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderExecutor(
    private val paymentService: PaymentService,
    private val pspClient: PSPClientPort,
    private val meterRegistry: MeterRegistry,
    private val pspResultCache: PspResultCachePort,
    @Qualifier("paymentOrderExecutorPoolConfig") private val pspExecutor: ThreadPoolTaskExecutor
) : BaseSingleKafkaConsumer<PaymentOrderCreated>() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun filter(envelope: EventEnvelope<PaymentOrderCreated>): Boolean {
        // Accept all for now, can add filtering logic later
        return true
    }

    override fun domainContext(
        envelope: EventEnvelope<PaymentOrderCreated>,
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>
    ): Map<String, String> {
        val event = envelope.data
        return mapOf(
            LogFields.CONSUMER_GROUP to "PAYMENT_ORDER_EXECUTOR",
            LogFields.PUBLIC_PAYMENT_ID to event.publicPaymentId,
            LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.aggregateId
        )
    }

    @Transactional
    override fun consume(
        envelope: EventEnvelope<PaymentOrderCreated>,
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>
    ) {
        val totalStart = System.currentTimeMillis()
        val event = envelope.data
        val order = paymentService.mapEventToDomain(event)

        if (order.status != PaymentOrderStatus.INITIATED) {
            logger.info("⏩ Skipping already processed order(status=${order.status})")
            return
        }

        val cacheStart = System.currentTimeMillis()
        try {
            val key = order.paymentOrderId.toString()
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
            paymentService.processPspResult(event = event, pspStatus = status)
            pspResultCache.remove(key)
            val dbEnd = System.currentTimeMillis()
            logger.info("TIMING: processPspResult (DB/write) took ${dbEnd - dbStart} ms for $key")

            val totalEnd = System.currentTimeMillis()
            logger.info("TIMING: Total handler time: ${totalEnd - totalStart} ms for $key")

        } catch (e: TransientDataAccessException) {
            logger.error("🔄 Transient DB error for $order, will retry/DLQ", e)
            throw e
        } catch (e: RetriableException) {
            logger.error("🔄 Transient Kafka error for $order, will retry/DLQ", e)
            throw e
        } catch (t: Throwable) {
            logger.error("❌ Fatal error for $order, sending to DLQ", t)
            throw ListenerExecutionFailedException("Fatal", t)
        }
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        val pspCallStart = System.currentTimeMillis()
        val future = pspExecutor.submit<PaymentOrderStatus> { pspClient.charge(order) }
        return try {
            future.get(1, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("PSP call timed out for paymentOrderId=${order.paymentOrderId}, scheduling retry")
            future.cancel(true)
            return PaymentOrderStatus.TIMEOUT
        } finally {
            meterRegistry.counter("SafePspCall.total", "status", "success").increment()
            val pspCallEnd = System.currentTimeMillis()
            logger.info("TIMING: Real PSP call took ${pspCallEnd - pspCallStart} ms for paymentOrderId=${order.paymentOrderId}")
        }
    }

    @KafkaListener(
        topics = ["payment_order_created_topic"],
        groupId = "payment-order-executor-group",
        containerFactory = "payment_order_created_topic-factory"
    )
    fun onMessage(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        handle(record)
    }
}