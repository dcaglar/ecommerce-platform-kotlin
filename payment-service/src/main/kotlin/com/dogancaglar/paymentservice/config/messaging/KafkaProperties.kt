package com.dogancaglar.paymentservice.config.messaging

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