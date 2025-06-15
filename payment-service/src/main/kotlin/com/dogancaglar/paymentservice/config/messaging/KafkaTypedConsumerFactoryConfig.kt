// KafkaTypedConsumerFactoryConfig.kt
package com.dogancaglar.paymentservice.config.messaging

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.config.serialization.EventEnvelopeDeserializer
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory
import org.springframework.util.backoff.FixedBackOff

@Configuration
@EnableConfigurationProperties(DynamicKafkaConsumersProperties::class)
class KafkaTypedConsumerFactoryConfig(
    private val dynamicProps: DynamicKafkaConsumersProperties,
    private val bootKafkaProps: KafkaProperties,
    private val meterRegistry: MeterRegistry
) {

    @Bean("custom-kafka-consumer-factory-for-micrometer")
    fun defaultKafkaConsumerFactory(): DefaultKafkaConsumerFactory<String, EventEnvelope<*>> {
        val configs = bootKafkaProps.buildConsumerProperties().toMutableMap()
        configs[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            org.springframework.kafka.support.serializer.ErrorHandlingDeserializer::class.java
        configs[org.springframework.kafka.support.serializer.ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS] =
            EventEnvelopeDeserializer::class.java.name

        return DefaultKafkaConsumerFactory<String, EventEnvelope<*>>(configs).apply {
            addListener(MicrometerConsumerListener(meterRegistry))
        }
    }


    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configs = bootKafkaProps.buildProducerProperties()
        return org.springframework.kafka.core.DefaultKafkaProducerFactory<String, Any>(configs).apply {
            addListener(MicrometerProducerListener(meterRegistry)) // <-- This is the key line
        }
    }


    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> =
        KafkaTemplate(producerFactory)

    @Bean
    fun errorHandler(kafkaTemplate: KafkaTemplate<String, Any>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { rec, _ ->
            TopicPartition("${rec.topic()}.DLQ", rec.partition())
        }
        return DefaultErrorHandler(recoverer, FixedBackOff(1_000L, 4))
    }

    private fun <T : Any> createTypedFactory(
        clientId: String,
        concurrency: Int,
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        consumerFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>> =
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>>().apply {
            this.consumerFactory = consumerFactory
            containerProperties.clientId = clientId
            containerProperties.isMicrometerEnabled = true
            @Suppress("UNCHECKED_CAST")
            setRecordInterceptor(interceptor as RecordInterceptor<String, EventEnvelope<T>>)
            setCommonErrorHandler(errorHandler)
            containerProperties.ackMode = ContainerProperties.AckMode.RECORD
            setConcurrency(concurrency)
        }

    @Bean("payment_order_created_topic-factory")
    fun paymentOrderCreatedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ) = dynamicProps.dynamicConsumers
        .first { it.topic == EventMetadatas.PaymentOrderCreatedMetadata.topic }
        .let { cfg ->
            createTypedFactory<PaymentOrderCreated>(
                clientId = cfg.id,
                concurrency = cfg.concurrency,
                interceptor = interceptor,
                consumerFactory = customFactory,
                errorHandler = errorHandler
            )
        }

    @Bean("payment_order_retry_request_topic-factory")
    fun paymentRetryRequestedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ) = dynamicProps.dynamicConsumers
        .first { it.topic == EventMetadatas.PaymentOrderRetryRequestedMetadata.topic }
        .let { cfg ->
            createTypedFactory<PaymentOrderRetryRequested>(
                clientId = cfg.id,
                concurrency = cfg.concurrency,
                interceptor = interceptor,
                consumerFactory = customFactory,
                errorHandler = errorHandler
            )
        }

    @Bean("payment_status_check_scheduler_topic-factory")
    fun paymentStatusCheckExecutorFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ) = dynamicProps.dynamicConsumers
        .first { it.topic == EventMetadatas.PaymentOrderStatusCheckScheduledMetadata.topic }
        .let { cfg ->
            createTypedFactory<PaymentOrderStatusCheckRequested>(
                clientId = cfg.id,
                concurrency = cfg.concurrency,
                interceptor = interceptor,
                consumerFactory = customFactory,
                errorHandler = errorHandler
            )
        }

    @Bean
    fun mdcRecordInterceptor(): RecordInterceptor<String, EventEnvelope<*>> = RecordInterceptor { record, _ ->
        fun header(key: String) = record.headers().lastHeader(key)?.value()?.let { String(it) }
        listOf(
            com.dogancaglar.common.logging.LogFields.TRACE_ID,
            com.dogancaglar.common.logging.LogFields.EVENT_ID,
            com.dogancaglar.common.logging.LogFields.PARENT_EVENT_ID
        ).forEach { key -> header(key)?.let { org.slf4j.MDC.put(key, it) } }
        org.slf4j.MDC.put(
            com.dogancaglar.common.logging.LogFields.AGGREGATE_ID,
            record.value()?.aggregateId ?: record.key()
        )
        record
    }

    @Bean
    fun messageHandlerMethodFactory(): DefaultMessageHandlerMethodFactory = DefaultMessageHandlerMethodFactory()
}