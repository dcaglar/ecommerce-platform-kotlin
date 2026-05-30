package com.dogancaglar.common.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.metadata.EventMetaDataRegistry
import com.dogancaglar.common.kafka.publisher.PaymentEventPublisher
import com.dogancaglar.common.kafka.serde.EventEnvelopeKafkaSerializer
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.MicrometerProducerListener
import org.springframework.kafka.core.KafkaAdmin
import org.apache.kafka.clients.admin.AdminClientConfig

/**
 * Auto-configuration for shared Kafka producer infrastructure.
 *
 * Provides two named producer factories (sync low-latency and batch high-throughput),
 * corresponding KafkaTemplates, KafkaTxExecutors, PaymentEventPublishers, and the
 * shared EventMetaDataRegistry.
 *
 * Configuration is driven entirely by each module's application.yml via spring.kafka.*
 * (bootstrap-servers, security, ssl, sasl) plus the module-specific producer tuning
 * in app.kafka.*. No System.getenv() calls — Spring Boot's KafkaProperties handles
 * environment variable injection transparently.
 *
 * Boundaries:
 *   ✅ Allowed: ProducerFactory, KafkaTemplate, Serializer/Deserializer,
 *              PaymentEventPublisher, EventMetaDataRegistry, KafkaAdmin
 *   ❌ Prohibited: @KafkaListener, NewTopic beans, domain event DTOs/schemas
 */
@AutoConfiguration
class KafkaProducerAutoConfig(
    private val bootKafkaProps: KafkaProperties,
    @param:Value("\${app.instance-id}") private val instanceId: String,
    @param:Value("\${spring.application.name}") private val appName: String,
) {

    /** Common baseline from Spring Boot props + safe reliability knobs */
    private fun baseProps(): MutableMap<String, Any> =
        bootKafkaProps.buildProducerProperties(null).toMutableMap().apply {
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

    @Bean("syncPaymentEventProducerFactory")
    fun syncPaymentEventProducerFactory(mr: MeterRegistry): DefaultKafkaProducerFactory<String, EventEnvelope<*>> =
        DefaultKafkaProducerFactory<String, EventEnvelope<*>>(
            lowLatencyOverrides(baseProps()).apply {
                put(ProducerConfig.CLIENT_ID_CONFIG, "$appName-sync-payment-tx-producer-client")
            }
        ).apply {
            addListener(MicrometerProducerListener(mr))
        }

    @Bean("syncPaymentEventKafkaTemplate")
    fun syncPaymentEventKafkaTemplate(
        @Qualifier("syncPaymentEventProducerFactory") pf: DefaultKafkaProducerFactory<String, EventEnvelope<*>>
    ) = KafkaTemplate(pf)

    @Bean("syncPaymentEventPublisher")
    fun syncPaymentEventPublisher(
        @Qualifier("syncPaymentEventKafkaTemplate") kt: KafkaTemplate<String, EventEnvelope<*>>,
        eventMetaDataRegistry: EventMetaDataRegistry,
        mr: MeterRegistry
    ) = PaymentEventPublisher(kt, eventMetaDataRegistry, mr)



    /** ---------- BATCH / THROUGHPUT PRODUCER (outbox, retry dispatcher) ---------- */
    private fun batchOverrides(m: MutableMap<String, Any>) = m.apply {
        put(ProducerConfig.LINGER_MS_CONFIG, 25)         // coalesce sends
        put(ProducerConfig.BATCH_SIZE_CONFIG, 262_144)   // 256 KiB
        put(ProducerConfig.BUFFER_MEMORY_CONFIG, 134_217_728) // 128 MiB
    }

    @Bean("batchPaymentProducerFactory")
    fun batchPaymentProducerFactory(mr: MeterRegistry): DefaultKafkaProducerFactory<String, EventEnvelope<*>> =
        DefaultKafkaProducerFactory<String, EventEnvelope<*>>(
            batchOverrides(baseProps()).apply {
                put(ProducerConfig.CLIENT_ID_CONFIG, "$appName-batch-payment-tx-producer-client")
            }
        ).apply {
            addListener(MicrometerProducerListener(mr))
        }

    @Bean("batchPaymentKafkaTemplate")
    fun batchPaymentKafkaTemplate(
        @Qualifier("batchPaymentProducerFactory") pf: DefaultKafkaProducerFactory<String, EventEnvelope<*>>
    ) = KafkaTemplate(pf)

    @Bean("batchPaymentEventPublisher")
    fun batchPaymentEventPublisher(
        @Qualifier("batchPaymentKafkaTemplate") kt: KafkaTemplate<String, EventEnvelope<*>>,
        mr: MeterRegistry,
        eventMetaDataRegistry: EventMetaDataRegistry
    ) = PaymentEventPublisher(kt, eventMetaDataRegistry, mr)



    @Bean
    fun eventMetaDataRegistry() = EventMetaDataRegistry(PaymentEventMetadataCatalog.all)

    /** Explicit KafkaAdmin definition as per infrastructure boundary rules */
    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        val configs = HashMap<String, Any>()
        configs[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = bootKafkaProps.bootstrapServers.joinToString(",")
        // Include any SASL/SSL credentials here if needed for cluster admin tasks
        return KafkaAdmin(configs)
    }
}
