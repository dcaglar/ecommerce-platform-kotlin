// KafkaTypedConsumerFactoryConfig.kt
package com.dogancaglar.paymentservice.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.TOPICS
import com.dogancaglar.common.logging.GenericLogFields
import com.dogancaglar.paymentservice.config.kafka.EventEnvelopeKafkaDeserializer
import com.dogancaglar.paymentservice.domain.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionException
import org.springframework.dao.*
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.*
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import java.sql.SQLTransientException

@Configuration
class KafkaTypedConsumerFactoryConfig(
    private val dynamicProps: DynamicKafkaConsumersProperties,
    private val bootKafkaProps: KafkaProperties,
    private val meterRegistry: MeterRegistry
) {
    companion object {
        private val logger = LoggerFactory.getLogger(KafkaTypedConsumerFactoryConfig::class.java)
    }

    init {
        logger.info("Loaded dynamicConsumers: {}", dynamicProps.dynamicConsumers.map { it.topic })
    }

    @Bean("custom-kafka-consumer-factory-for-micrometer")
    fun defaultKafkaConsumerFactory(): DefaultKafkaConsumerFactory<String, EventEnvelope<*>> {
        val configs = bootKafkaProps.buildConsumerProperties().toMutableMap()
        configs[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            ErrorHandlingDeserializer::class.java
        configs[ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS] =
            EventEnvelopeKafkaDeserializer::class.java

        return DefaultKafkaConsumerFactory<String, EventEnvelope<*>>(configs).apply {
            addListener(MicrometerConsumerListener(meterRegistry))
        }
    }


    @Bean("dlqProducerFactory")
    fun producerFactory(): ProducerFactory<String, Any> {
        val configs = bootKafkaProps.buildProducerProperties()
        return DefaultKafkaProducerFactory<String, Any>(configs).apply {
            addListener(MicrometerProducerListener(meterRegistry)) // <-- This is the key line
        }
    }


    @Bean("dlqKafkaTemplate")
    fun dlqKafkaTemplate(@Qualifier("dlqProducerFactory") producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> =
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
        @Qualifier("dlqKafkaTemplate") kafkaTemplate: KafkaTemplate<String, Any>,
        kafkaExponentialBackOff: ExponentialBackOffWithMaxRetries
    ): DefaultErrorHandler {

        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { rec, _ ->
            when (rec.topic()) {
                TOPICS.PAYMENT_ORDER_CREATED ->
                    TopicPartition(TOPICS.PAYMENT_ORDER_CREATED_DLQ, rec.partition())

                TOPICS.PAYMENT_ORDER_RETRY ->
                    TopicPartition(TOPICS.PAYMENT_ORDER_RETRY_DLQ, rec.partition())

                TOPICS.PAYMENT_STATUS_CHECK_SCHEDULER ->
                    TopicPartition(TOPICS.PAYMENT_STATUS_CHECK_SCHEDULER_DLQ, rec.partition())

                else ->
                    TopicPartition(rec.topic() + ".DLQ", rec.partition())
            }
        }

        return DefaultErrorHandler(recoverer, kafkaExponentialBackOff).apply {
            setCommitRecovered(true)        // commit offset after successful DLQ publish
            setAckAfterHandle(false)
            setRetryListeners(
                RetryListener { rec, ex, deliveryAttempt ->
                    logger.warn(
                        "Retry #{} for {}-{}@{}: {} - {}",
                        deliveryAttempt, rec.topic(), rec.partition(), rec.offset(),
                        ex::class.java.simpleName, ex.message
                    )
                }
            )
            // your retryable / not-retryable adds stay the same

            addRetryableExceptions(
                RetriableException::class.java,
                TransientDataAccessException::class.java,
                CannotAcquireLockException::class.java,
                SQLTransientException::class.java,
            )
            addNotRetryableExceptions(
                IllegalArgumentException::class.java,
                NullPointerException::class.java,
                ClassCastException::class.java,
                ConversionException::class.java,
                DeserializationException::class.java,
                SerializationException::class.java,
                MethodArgumentNotValidException::class.java,
                DuplicateKeyException::class.java,
                DataIntegrityViolationException::class.java,
                NonTransientDataAccessException::class.java,
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
            containerProperties.ackMode = ContainerProperties.AckMode.RECORD
            setConcurrency(concurrency)
            isBatchListener = false
        }

    @Bean("${TOPICS.PAYMENT_ORDER_CREATED}-factory")
    fun paymentOrderCreatedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderCreated>> {
        val cfg = cfgFor(EventMetadatas.PaymentOrderCreatedMetadata.topic, "${TOPICS.PAYMENT_ORDER_CREATED}-factory")
        return createTypedFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
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
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderRetryRequested>> {
        val cfg =
            cfgFor(EventMetadatas.PaymentOrderRetryRequestedMetadata.topic, "${TOPICS.PAYMENT_ORDER_RETRY}-factory")
        return createTypedFactory(
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
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderStatusCheckRequested>> {
        val cfg = cfgFor(
            EventMetadatas.PaymentOrderStatusCheckScheduledMetadata.topic,
            "${TOPICS.PAYMENT_STATUS_CHECK_SCHEDULER}-factory"
        )
        return createTypedFactory(
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


    private fun cfgFor(topic: String, beanName: String): DynamicKafkaConsumersProperties.DynamicConsumer =
        dynamicProps.dynamicConsumers.firstOrNull { it.topic == topic }
            ?: error("Missing app.kafka.dynamic-consumers entry for topic '$topic' (required by bean '$beanName').")

}