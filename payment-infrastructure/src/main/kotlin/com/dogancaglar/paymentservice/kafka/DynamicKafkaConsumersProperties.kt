package com.dogancaglar.paymentservice.kafka

open class DynamicKafkaConsumersProperties {
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