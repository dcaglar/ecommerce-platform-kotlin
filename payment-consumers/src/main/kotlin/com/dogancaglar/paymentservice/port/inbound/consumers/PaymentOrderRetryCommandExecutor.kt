package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.PspResultCachePort
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderRetryCommandExecutor(
    private val processPspResultUseCase: ProcessPspResultUseCase,
    private val pspClient: PaymentGatewayPort,
    private val meterRegistry: MeterRegistry,
    private val pspResultCache: PspResultCachePort,
    private val kafkaTx: KafkaTxExecutor,
    @Qualifier("paymentOrderExecutorPoolConfig") private val pspExecutor: ThreadPoolTaskExecutor
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_RETRY],
        containerFactory = "${Topics.PAYMENT_ORDER_RETRY}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_RETRY
    )
    fun onMessage(record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>) {
        val totalStart = System.currentTimeMillis()

        val envelope = record.value()
        val event = envelope.data
        val order: PaymentOrder = PaymentOrderDomainEventMapper.fromEvent(event)

        logger.info("Processing PaymentOrderRetryRequested, orderId={}", order.publicPaymentOrderId)

        // Compute offsets once (manual ack mode)
        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))

        // Skip path: commit offset-only in a Kafka TX
        if (order.status != PaymentOrderStatus.INITIATED) {
            kafkaTx.run(offsets, CONSUMER_GROUPS.PAYMENT_ORDER_RETRY) { /* no-op */ }
            logger.info("⏩ Skipped non-INITIATED; offset committed")
            return
        }

        val key = order.paymentOrderId
        val status = pspResultCache.get(key)?.let {
            logger.info("♻️ PSP cache hit for {}", key)
            PaymentOrderStatus.valueOf(it)
        } ?: run {
            val result = safePspCall(order)
            pspResultCache.put(key, result.name)
            result
        }

        // Kafka TX: use case (DB state + any publish) + offsets
        kafkaTx.run(offsets, CONSUMER_GROUPS.PAYMENT_ORDER_RETRY) {
            val dbStart = System.currentTimeMillis()
            processPspResultUseCase.processPspResult(event = event, pspStatus = status)
            logger.info("TIMING: processPspResult took {} ms for {}", (System.currentTimeMillis() - dbStart), key)
        }

        logger.info("TIMING: total handler took {} ms for {}", (System.currentTimeMillis() - totalStart), key)
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