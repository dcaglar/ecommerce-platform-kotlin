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
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderPspCallExecutor(
    private val processPspResult: ProcessPspResultUseCase,
    private val pspClient: PaymentGatewayPort,
    private val meterRegistry: MeterRegistry,
    private val orders: PaymentOrderRepository,
    private val kafkaTx: KafkaTxExecutor,
    @Qualifier("paymentOrderPspPool") private val pspExecutor: ThreadPoolTaskExecutor
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_PSP_CALL_REQUESTED],
        containerFactory = "${Topics.PAYMENT_ORDER_PSP_CALL_REQUESTED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_PSP_CALL_EXECUTOR
    )
    fun onPspRequested(record: ConsumerRecord<String, EventEnvelope<PaymentOrderPspCallRequested>>) {
        val env = record.value()
        val work = env.data
        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))

        LogContext.with(env) {
            val id = PaymentOrderId(work.paymentOrderId.toLong())

            // 1) CAS attempt fence
            if (!orders.casLockAttempt(id, expectedAttempt = work.retryCount)) {
                kafkaTx.run(offsets, CONSUMER_GROUPS.PAYMENT_ORDER_PSP_CALL_EXECUTOR) {}
                logger.info("⏩ Stale attempt={} agg={}", work.retryCount, env.aggregateId)
                return@with
            }

            // 2) Build domain snapshot from event (no DB load)
            val orderForPsp: PaymentOrder = PaymentOrderDomainEventMapper.fromEvent(work)

            // 3) Call PSP
            val status = safePspCall(orderForPsp)

            // 4) Persist + bump attempt atomically with offset commit
            kafkaTx.run(offsets, CONSUMER_GROUPS.PAYMENT_ORDER_PSP_CALL_EXECUTOR) {
                processPspResult.processPspResult(event = work, pspStatus = status)
                orders.bumpAttempt(id, fromAttempt = work.retryCount)
            }
        }
    }

    private val pspLatency: Timer = Timer.builder("psp_call_latency")
        .publishPercentileHistogram()        // -> *_seconds_bucket in Prom
        .register(meterRegistry)

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        val sample = Timer.start(meterRegistry)
        var resultLabel = "EXCEPTION"        // default if an unexpected error bubbles
        val future = pspExecutor.submit<PaymentOrderStatus> { pspClient.charge(order) }
        return try {
            val status = future.get(1, TimeUnit.SECONDS)
            resultLabel = status.name
            status
        } catch (_: TimeoutException) {
            future.cancel(true)
            resultLabel = "TIMEOUT"
            PaymentOrderStatus.TIMEOUT
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