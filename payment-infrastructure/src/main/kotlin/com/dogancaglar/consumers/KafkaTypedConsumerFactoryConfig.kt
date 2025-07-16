// KafkaTypedConsumerFactoryConfig.kt
package com.dogancaglar.consumers

import com.dogancaglar.application.PaymentOrderCreated
import com.dogancaglar.application.PaymentOrderRetryRequested
import com.dogancaglar.application.PaymentOrderStatusCheckRequested
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.TOPICS
import com.dogancaglar.common.logging.GenericLogFields
import com.dogancaglar.infrastructure.config.kafka.deserialization.EventEnvelopeDeserializer
import com.dogancaglar.payment.application.events.EventMetadatas
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionException
import org.springframework.dao.*
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import java.sql.SQLTransientException

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
            ErrorHandlingDeserializer::class.java
        configs[ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS] =
            EventEnvelopeDeserializer::class.java.name

        return DefaultKafkaConsumerFactory<String, EventEnvelope<*>>(configs).apply {
            addListener(MicrometerConsumerListener(meterRegistry))
        }
    }


    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configs = bootKafkaProps.buildProducerProperties()
        return DefaultKafkaProducerFactory<String, Any>(configs).apply {
            addListener(MicrometerProducerListener(meterRegistry)) // <-- This is the key line
        }
    }


    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> =
        KafkaTemplate(producerFactory)

    @Bean
    fun kafkaExponentialBackOff(): ExponentialBackOffWithMaxRetries =
        ExponentialBackOffWithMaxRetries(4).apply {
            initialInterval = 2_000L
            multiplier = 2.0
            maxInterval = 30_000L
        }

    @Bean
    fun errorHandler(
        kafkaTemplate: KafkaTemplate<String, Any>,
        kafkaExponentialBackOff: ExponentialBackOffWithMaxRetries
    ): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { rec, _ ->
            TopicPartition("${rec.topic()}.DLQ", rec.partition())
        }
        return DefaultErrorHandler(recoverer, kafkaExponentialBackOff).apply {
            // Retryable exceptions
            addRetryableExceptions(
                RetriableException::class.java,
                TransientDataAccessException::class.java,
                CannotAcquireLockException::class.java,
                SQLTransientException::class.java,
            )
            // Non-retryable exceptions
            addNotRetryableExceptions(
                IllegalArgumentException::class.java,
                NullPointerException::class.java,
                ClassCastException::class.java,
                ConversionException::class.java,
                DeserializationException::class.java,
                SerializationException::class.java,
                MethodArgumentNotValidException::class.java,
                // Data duplicates & constraint violations
                DuplicateKeyException::class.java,
                DataIntegrityViolationException::class.java,
                NonTransientDataAccessException::class.java,
                IllegalArgumentException::class.java,
                KafkaException::class.java,
                org.springframework.kafka.KafkaException::class.java
            )
        }
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
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
            setConcurrency(concurrency)
            isBatchListener = true
        }

    @Bean("${TOPICS.PAYMENT_ORDER_CREATED}-factory")
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
                concurrency = 1,
                interceptor = interceptor,
                consumerFactory = customFactory,
                errorHandler = errorHandler
            )
        }

    @Bean("${TOPICS.PAYMENT_ORDER_RETRY}-factory")
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

    @Bean("${TOPICS.PAYMENT_STATUS_CHECK_SCHEDULER}-factory")
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
            GenericLogFields.TRACE_ID,
            GenericLogFields.EVENT_ID,
            GenericLogFields.PARENT_EVENT_ID
        ).forEach { key -> header(key)?.let { MDC.put(key, it) } }
        MDC.put(
            GenericLogFields.AGGREGATE_ID,
            record.value()?.aggregateId ?: record.key()
        )
        record
    }

    @Bean
    fun messageHandlerMethodFactory(): DefaultMessageHandlerMethodFactory = DefaultMessageHandlerMethodFactory()
}