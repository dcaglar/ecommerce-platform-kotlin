// DynamicKafkaConsumersProperties.kt
package com.dogancaglar.paymentservice.config.messaging

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.kafka")
class DynamicKafkaConsumersProperties {
    var dynamicConsumers: List<DynamicConsumer> = mutableListOf()

    class DynamicConsumer {
        lateinit var id: String
        lateinit var topic: String
        lateinit var groupId: String
        lateinit var className: String

        /** number of threads = number of partitions */
        var concurrency: Int = 1
    }
}