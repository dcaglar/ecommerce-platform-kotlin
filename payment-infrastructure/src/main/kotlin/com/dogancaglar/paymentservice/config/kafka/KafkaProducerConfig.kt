package com.dogancaglar.paymentservice.config.kafka

import com.dogancaglar.common.event.EventEnvelope
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.MicrometerProducerListener
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaProducerConfig(
    private val bootKafkaProps: KafkaProperties
) {
    @Bean("businessEventProducerFactory")
    fun paymentOrderEventProducerFactory(meterRegistry: MeterRegistry): DefaultKafkaProducerFactory<String, EventEnvelope<*>> {
        val props = bootKafkaProps.buildProducerProperties()
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = EventEnvelopeKafkaSerializer::class.java
        props[JsonSerializer.ADD_TYPE_INFO_HEADERS] = false
        return DefaultKafkaProducerFactory<String, EventEnvelope<*>>(props).apply {
            addListener(MicrometerProducerListener(meterRegistry))
        }
    }

    @Bean("businessEventKafkaTemplate")
    fun paymentOrderEventKafkaTemplate(
        @Qualifier("businessEventProducerFactory") paymentOrderEventProducerFactory: DefaultKafkaProducerFactory<String, EventEnvelope<*>>
    ): KafkaTemplate<String, EventEnvelope<*>> {
        return KafkaTemplate(paymentOrderEventProducerFactory)
    }
}