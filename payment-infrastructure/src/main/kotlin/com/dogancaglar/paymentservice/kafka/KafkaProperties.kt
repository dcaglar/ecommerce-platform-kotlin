package com.dogancaglar.paymentservice.kafka

open class KafkaTopicsProperties {
    var payment_order_created_topic: TopicConfig = TopicConfig()
    var payment_order_created_topic_dlq: TopicConfig = TopicConfig()
    var payment_order_retry_request_topic: TopicConfig = TopicConfig()
    var payment_order_retry_request_topic_dlq: TopicConfig = TopicConfig()
    var payment_status_check_scheduler_topic: TopicConfig = TopicConfig()
    var payment_status_check_scheduler_topic_dlq: TopicConfig = TopicConfig()
    var payment_order_succeded_topic: TopicConfig = TopicConfig()
    var payment_order_succeded_topic_dlq: TopicConfig = TopicConfig()
    // add more as needed

    class TopicConfig {
        var partitions: Int = 1
        var replicas: Short = 1
    }
}