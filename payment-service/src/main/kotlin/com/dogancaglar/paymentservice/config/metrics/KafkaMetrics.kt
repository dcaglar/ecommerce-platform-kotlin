package com.dogancaglar.paymentservice.config.metrics

object KafkaMetrics {
    // Standard Micrometer/Kafka metrics (always lowercase and snake_case for Prometheus)
    const val CONSUMER_RECORDS_CONSUMED_TOTAL = "kafka_consumer_records_consumed_total"
    const val CONSUMER_LAG = "kafka_consumer_records_lag"
    const val CONSUMER_ERRORS_TOTAL = "kafka_consumer_errors_total"
    const val PRODUCER_RECORDS_SENT_TOTAL = "kafka_producer_records_sent_total"
    const val PRODUCER_ERRORS_TOTAL = "kafka_producer_record_error_total"
    // ...add more as you use/monitor them

    // Helper for a custom consumer business metric
    fun customConsumerProcessed(topic: String, group: String) =
        "custom_consumer_${topic}_${group}_processed_total"

    // Helper for a custom error metric per topic/group
    fun customConsumerErrors(topic: String, group: String) =
        "custom_consumer_${topic}_${group}_errors_total"
}
