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
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentOrderPspCallExecutor(
    private val processPspResult: ProcessPspResultUseCase,
    private val pspClient: PaymentGatewayPort,
    private val meterRegistry: MeterRegistry,
    private val kafkaTx: KafkaTxExecutor
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val pspLatency: Timer = Timer.builder("psp_call_latency")
        .publishPercentileHistogram()
        .register(meterRegistry)

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
        val groupMeta = consumer.groupMetadata()

        LogContext.with(env) {
            val id = PaymentOrderId(work.paymentOrderId.toLong())

            // Build domain snapshot (no DB load)
            val orderForPsp: PaymentOrder = PaymentOrderDomainEventMapper.fromEvent(work)

            // Call PSP (adapter handles timeouts/interrupts)
            var status: PaymentOrderStatus? = null
            val sample = Timer.start(meterRegistry)
            try {
                status = pspClient.charge(orderForPsp)

                // Persist + publish under Kafka tx
                kafkaTx.run(offsets, groupMeta) {
                    processPspResult.processPspResult(event = work, pspStatus = status!!)
                }
            } finally {
                sample.stop(pspLatency)
                meterRegistry.counter(
                    "psp_calls_total",
                    "result",
                    status?.name ?: "EXCEPTION"
                ).increment()
            }
        }
    }
}