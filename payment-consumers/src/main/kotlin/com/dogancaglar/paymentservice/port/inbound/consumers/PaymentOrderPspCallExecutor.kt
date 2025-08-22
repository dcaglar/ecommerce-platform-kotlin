package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentGatewayPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderPspCallExecutor(
    private val processPspResult: ProcessPspResultUseCase,
    private val pspClient: PaymentGatewayPort,
    private val meterRegistry: MeterRegistry,
    private val kafkaTx: KafkaTxExecutor,
    @Qualifier("paymentOrderPspPool") private val pspExecutor: ThreadPoolTaskExecutor
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_PSP_CALL_REQUESTED],
        containerFactory = "${Topics.PAYMENT_ORDER_PSP_CALL_REQUESTED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_PSP_CALL_EXECUTOR
    )
    fun onPspRequested(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderPspCallRequested>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val env = record.value()
        val work = env.data
        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta =
            consumer.groupMetadata()
        LogContext.with(env) {
            val id = PaymentOrderId(work.paymentOrderId.toLong())


            // 2) Build domain snapshot from event (no DB load)
            val orderForPsp: PaymentOrder = PaymentOrderDomainEventMapper.fromEvent(work)

            // 3) Call PSP-idempotent
            val status = safePspCall(orderForPsp)

            // 4) Persist + publish kafka, since under kafka tx, offset is not moving forward till event is published
            kafkaTx.run(offsets, groupMeta) {
                processPspResult.processPspResult(event = work, pspStatus = status)
            }
        }
    }

    private val pspLatency: Timer = Timer.builder("psp_call_latency")
        .publishPercentileHistogram()        // -> *_seconds_bucket in Prom
        .register(meterRegistry)

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        val sample = Timer.start(meterRegistry)
        var resultLabel = "EXCEPTION"        // default if an unexpected error bubbles
        try {
            val future = pspExecutor.submit<PaymentOrderStatus> { pspClient.charge(order) }
            return try {
                val status = future.get(1, TimeUnit.SECONDS)
                resultLabel = status.name
                status
            } catch (t: TimeoutException) {
                logger.warn("PSP call timeout: {}", t.message)
                future.cancel(true)
                resultLabel = "TIMEOUT"
                PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
            } catch (e: InterruptedException) {
                logger.warn("PSP call interrupted: {}", e.message)
                future.cancel(true)
                Thread.currentThread().interrupt()
                resultLabel = "INTERRUPTED"
                PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
            }
        } catch (e: RejectedExecutionException) {
            // pool saturated; fail fast without blocking listener
            logger.warn("PSP call rejected due to executor saturation: {}", e.message)
            resultLabel = "REJECTED"
            return PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT     // or a distinct status
        } catch (e: Exception) {
            // Let DefaultErrorHandler handle it, but tag metrics correctly
            resultLabel = "EXCEPTION"
            throw e
        } finally {
            sample.stop(pspLatency)
            meterRegistry.counter("psp_calls_total", "result", resultLabel).increment()
        }
    }
}