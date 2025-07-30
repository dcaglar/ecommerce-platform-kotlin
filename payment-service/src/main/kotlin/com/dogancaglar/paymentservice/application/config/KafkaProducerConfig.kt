package com.dogancaglar.paymentservice.application.config

import com.dogancaglar.common.event.EventEnvelope
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
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
    @Bean
    fun paymentOrderEventProducerFactory(meterRegistry: MeterRegistry): DefaultKafkaProducerFactory<String, EventEnvelope<*>> {
        val props = bootKafkaProps.buildProducerProperties()
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
        props[JsonSerializer.ADD_TYPE_INFO_HEADERS] = false
        return DefaultKafkaProducerFactory<String, EventEnvelope<*>>(props).apply {
            addListener(MicrometerProducerListener(meterRegistry))
        }
    }

    @Bean
    fun paymentOrderEventKafkaTemplate(
        paymentOrderEventProducerFactory: DefaultKafkaProducerFactory<String, EventEnvelope<*>>
    ): KafkaTemplate<String, EventEnvelope<*>> {
        return KafkaTemplate(paymentOrderEventProducerFactory)
    }
}

