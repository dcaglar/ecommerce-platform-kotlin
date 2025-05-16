package com.dogancaglar.paymentservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "kafka")
class KafkaProperties {
    lateinit var bootstrapServers: String
    var trustedPackages: String = "*"

    lateinit var topics: Topics
    lateinit var consumer: Consumer

    class Topics {
        lateinit var delayScheduling: String
        lateinit var paymentOrderRetry: String
        lateinit var paymentOrderCreated: String
        lateinit var paymentStatusCheck: String
    }

    class Consumer {
        lateinit var groups: Groups

        class Groups {
            lateinit var paymentRetryExecutor: String
            lateinit var paymentExecutor: String
        }
    }
}