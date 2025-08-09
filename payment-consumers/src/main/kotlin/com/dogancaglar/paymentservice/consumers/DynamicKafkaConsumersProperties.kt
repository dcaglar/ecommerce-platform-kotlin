// DynamicKafkaConsumersProperties.kt
package com.dogancaglar.paymentservice.consumers

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class ConsumersPropertiesConfig {
    @Bean
    @ConfigurationProperties("app.kafka")
    fun dynamicKafkaConsumersProperties() = DynamicKafkaConsumersProperties()
}

class DynamicKafkaConsumersProperties {
    var dynamicConsumers: List<DynamicConsumer> = emptyList()

    class DynamicConsumer {
        var id: String = ""
        var topic: String = ""
        var groupId: String = ""
        var className: String = ""
        var concurrency: Int = 1
    }
}