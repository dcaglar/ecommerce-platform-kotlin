package com.dogancaglar.paymentservice.config.kafka

import com.dogancaglar.common.event.EventEnvelope
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.MicrometerProducerListener

@Configuration
class KafkaProducerConfig(
    //@param:Qualifier
    private val bootKafkaProps: KafkaProperties,
    @param:Value("\${app.instance-id}") private val instanceId: String,
    @param:Value("\${spring.application.name}") private val appName: String,
) {

    /** Common baseline from Spring Boot props + safe reliability knobs */
    private fun baseProps(): MutableMap<String, Any> =
        bootKafkaProps.buildProducerProperties().toMutableMap().apply {
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventEnvelopeKafkaSerializer::class.java)

            // reliability
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)

            // timeouts
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 20_000)
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60_000)
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 30_000)

            // compression
            put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd")
        }

    /** ---------- SYNC / LOW-LATENCY PRODUCER ---------- */
    private fun lowLatencyOverrides(m: MutableMap<String, Any>) = m.apply {
        put(ProducerConfig.LINGER_MS_CONFIG, 3)          // tiny wait
        put(ProducerConfig.BATCH_SIZE_CONFIG, 131_072)   // 128 KiB
        put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67_108_864) // 64 MiB
    }


    /** ---------- DEFAULT (used by @Component PaymentEventPublisher) ---------- */
    @Bean("syncPaymentEventProducerFactory")
    fun syncPaymentEventProducerFactory(mr: MeterRegistry): DefaultKafkaProducerFactory<String, EventEnvelope<*>> =
        DefaultKafkaProducerFactory<String, EventEnvelope<*>>(
            lowLatencyOverrides(baseProps()).apply {
                // 🔎 distinct id for enqueuer path
                put(ProducerConfig.CLIENT_ID_CONFIG, "${appName}-sync-payment-tx-producer-client")
            }
        ).apply {
            setTransactionIdPrefix("sync-payment-tx-$instanceId-")
            addListener(MicrometerProducerListener(mr))
        }

    @Bean("syncPaymentEventKafkaTemplate")
    fun syncPaymentEventKafkaTemplate(
        @Qualifier("syncPaymentEventProducerFactory") pf: DefaultKafkaProducerFactory<String, EventEnvelope<*>>
    ) = KafkaTemplate(pf).apply { isAllowNonTransactional = false }


    /** Provide a second PaymentEventPublisher wired to the batch template */
    @Bean("syncPaymentEventPublisher")
    fun syncPaymentEventPublisher(
        @Qualifier("syncPaymentEventKafkaTemplate") kt: KafkaTemplate<String, EventEnvelope<*>>,
        mr: MeterRegistry
    ) = com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher(kt, mr)



    @Bean("syncPaymentTx")
    fun syncPaymentTx(
        @Qualifier("syncPaymentEventKafkaTemplate")
        kt: KafkaTemplate<String, EventEnvelope<*>>
    ) = KafkaTxExecutor(kt)



    /** ---------- Batch path (separate client.id) ---------- */


    /** ---------- BATCH / THROUGHPUT PRODUCER (outbox, retrydispatcher) ---------- */
    private fun batchOverrides(m: MutableMap<String, Any>) = m.apply {
        put(ProducerConfig.LINGER_MS_CONFIG, 25)         // coalesce sends
        put(ProducerConfig.BATCH_SIZE_CONFIG, 262_144)   // 256 KiB
        put(ProducerConfig.BUFFER_MEMORY_CONFIG, 134_217_728) // 128 MiB
    }


    @Bean("batchPaymentProducerFactory")
    fun batchPaymentProducerFactory(mr: MeterRegistry): DefaultKafkaProducerFactory<String, EventEnvelope<*>> =
        DefaultKafkaProducerFactory<String, EventEnvelope<*>>(
            batchOverrides(baseProps()).apply {
                // 🔎 different id per producer factory
                put(ProducerConfig.CLIENT_ID_CONFIG, "${appName}-batch-payment-tx-producer-client")
            }
        ).apply {
            setTransactionIdPrefix("batch-payment-tx-$instanceId-")
            addListener(MicrometerProducerListener(mr))
        }

    @Bean("batchPaymentKafkaTemplate")
    fun batchPaymentKafkaTemplate(
        @Qualifier("batchPaymentProducerFactory") pf: DefaultKafkaProducerFactory<String, EventEnvelope<*>>
    ) = KafkaTemplate(pf).apply { isAllowNonTransactional = false }

    /** Provide a second PaymentEventPublisher wired to the batch template */
    @Bean("batchPaymentEventPublisher")
    fun batchPaymentEventPublisher(
        @Qualifier("batchPaymentKafkaTemplate") kt: KafkaTemplate<String, EventEnvelope<*>>,
        mr: MeterRegistry
    ) = com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher(kt, mr)

    @Bean("batchPaymentTx")
    fun batchPaymentTx(
        @Qualifier("batchPaymentKafkaTemplate")
        kt: KafkaTemplate<String, EventEnvelope<*>>
    ) = KafkaTxExecutor(kt)





}