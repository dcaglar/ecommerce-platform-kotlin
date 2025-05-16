package com.dogancaglar.paymentservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kafka")
class KafkaProperties { // ✅ <-- Add this line
    lateinit var bootstrapServers: String // ✅ Add this line
    var dynamicConsumers: List<DynamicConsumer> = emptyList()
    class DynamicConsumer {
        lateinit var id: String
        lateinit var topic: String
        lateinit var groupId: String
        lateinit var className: String
    }
}