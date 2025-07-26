package com.dogancaglar.paymentservice.adapter.outbound.kafka

import com.dogancaglar.common.event.TOPICS
import com.dogancaglar.paymentservice.kafka.KafkaTopicsProperties
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties::class)
class KafkaTopicsConfig(
    private val kafkaTopicsProperties: KafkaTopicsProperties
) {
    @Bean
    fun paymentOrderCreatedTopic(): NewTopic =
        NewTopic(
            TOPICS.PAYMENT_ORDER_CREATED,
            kafkaTopicsProperties.payment_order_created_topic.partitions,
            kafkaTopicsProperties.payment_order_created_topic.replicas
        )


    @Bean
    fun paymentOrderCreatedTopicDLQ(): NewTopic =
        NewTopic(
            TOPICS.PAYMENT_ORDER_CREATED_DLQ,
            kafkaTopicsProperties.payment_order_created_topic_dlq.partitions,
            kafkaTopicsProperties.payment_order_created_topic_dlq.replicas
        )

    @Bean
    fun paymentOrderRetryRequestTopic(): NewTopic =
        NewTopic(
            TOPICS.PAYMENT_ORDER_RETRY,
            kafkaTopicsProperties.payment_order_retry_request_topic.partitions,
            kafkaTopicsProperties.payment_order_retry_request_topic.replicas
        )

    @Bean
    fun paymentOrderRetryRequestTopicDLQ(): NewTopic =
        NewTopic(
            TOPICS.PAYMENT_ORDER_RETRY_DLQ,
            kafkaTopicsProperties.payment_order_retry_request_topic_dlq.partitions,
            kafkaTopicsProperties.payment_order_retry_request_topic_dlq.replicas
        )

    @Bean
    fun paymentOrderStatusCheckTopic(): NewTopic =
        NewTopic(
            TOPICS.PAYMENT_STATUS_CHECK_SCHEDULER,
            kafkaTopicsProperties.payment_status_check_scheduler_topic.partitions,
            kafkaTopicsProperties.payment_status_check_scheduler_topic.replicas
        )


    @Bean
    fun paymentOrderStatusCheckTopicDLQ(): NewTopic =
        NewTopic(
            TOPICS.PAYMENT_STATUS_CHECK_SCHEDULER_DLQ,
            kafkaTopicsProperties.payment_status_check_scheduler_topic_dlq.partitions,
            kafkaTopicsProperties.payment_status_check_scheduler_topic_dlq.replicas
        )


    @Bean
    fun paymentOrderSuccededTopic(): NewTopic =
        NewTopic(
            TOPICS.PAYMENT_ORDER_SUCCEEDED,
            kafkaTopicsProperties.payment_order_succeded_topic.partitions,
            kafkaTopicsProperties.payment_order_succeded_topic.replicas
        )


    @Bean
    fun paymentOrderSuccededTopicDLQ(): NewTopic =
        NewTopic(
            TOPICS.PAYMENT_ORDER_SUCCEEDED_DLQ,
            kafkaTopicsProperties.payment_order_succeded_topic_dlq.partitions,
            kafkaTopicsProperties.payment_order_succeded_topic_dlq.replicas
        )

}

@Bean
@ConfigurationProperties(prefix = "kafka.topics")
fun kafkaTopicsProperties(): KafkaTopicsProperties = KafkaTopicsProperties()

