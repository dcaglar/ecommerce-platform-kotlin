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
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.transaction.KafkaTransactionManager

@Configuration
class KafkaProducerConfig(
    private val bootKafkaProps: KafkaProperties,
    @Value("\${app.instance-id}") private val instanceId: String,
) {

    @Bean("businessEventProducerFactory")
    fun businessEventProducerFactory(meterRegistry: MeterRegistry): DefaultKafkaProducerFactory<String, EventEnvelope<*>> {
        // Start from application.yml producer props
        val props = bootKafkaProps.buildProducerProperties().toMutableMap()

        // Be explicit about serializers (value is your EventEnvelope serializer)
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = EventEnvelopeKafkaSerializer::class.java

        // Reliability knobs (fine to keep even if present in YAML)
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        props[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        props[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 120_000

        return DefaultKafkaProducerFactory<String, EventEnvelope<*>>(props).apply {
            // Transactional producer for EOS + offset commits
            setTransactionIdPrefix("business-tx-$instanceId-")
            addListener(MicrometerProducerListener(meterRegistry))
        }
    }

    @Bean
    fun kafkaTransactionManager(
        @Qualifier("businessEventProducerFactory")
        pf: ProducerFactory<String, EventEnvelope<*>>
    ): KafkaTransactionManager<String, EventEnvelope<*>> = KafkaTransactionManager(pf)

    @Bean("businessEventKafkaTemplate")
    fun paymentOrderEventKafkaTemplate(
        @Qualifier("businessEventProducerFactory") paymentOrderEventProducerFactory: DefaultKafkaProducerFactory<String, EventEnvelope<*>>
    ): KafkaTemplate<String, EventEnvelope<*>> {
        return KafkaTemplate(paymentOrderEventProducerFactory).apply {
            // for Java: setAllowNonTransactional(false)
            // for Kotlin property syntax:
            isAllowNonTransactional = false
        }
    }

}