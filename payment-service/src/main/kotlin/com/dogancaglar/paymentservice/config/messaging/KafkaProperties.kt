package com.dogancaglar.paymentservice.config.messaging

import com.dogancaglar.paymentservice.config.serialization.EventEnvelopeDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kafka")
class KafkaProperties {
    lateinit var bootstrapServers: String
    var dynamicConsumers: List<DynamicConsumer> = emptyList()

    class DynamicConsumer {
        lateinit var id: String
        lateinit var topic: String
        lateinit var groupId: String
        lateinit var className: String
    }
}

fun KafkaProperties.toMap(): Map<String, Any> {
    return mapOf(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to EventEnvelopeDeserializer::class.java,
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers
    )
}
