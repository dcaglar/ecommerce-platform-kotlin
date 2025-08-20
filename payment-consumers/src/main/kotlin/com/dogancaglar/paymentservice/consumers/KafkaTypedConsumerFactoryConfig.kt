// KafkaTypedConsumerFactoryConfig.kt
package com.dogancaglar.paymentservice.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.common.logging.GenericLogFields
import com.dogancaglar.paymentservice.domain.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
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
import org.springframework.kafka.listener.adapter.RecordFilterStrategy
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.serializer.DeserializationException
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
            val src = rec.topic()
            // If it's already a DLQ message, keep it in the same DLQ (or send to a "parking lot" if you want).
            val target = if (src.endsWith(".DLQ")) src else Topics.dlqOf(src)
            TopicPartition(target, rec.partition())
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
            )
        }
    }

    private fun <T : Any> createTypedFactory(
        clientId: String,
        concurrency: Int,
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        consumerFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler,
        ackMode: ContainerProperties.AckMode = ContainerProperties.AckMode.RECORD,
        expectedEventType: String? = null,        // ← NEW
        ackDiscarded: Boolean = true              // ← NEW
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>> =
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>>().apply {
            this.consumerFactory = consumerFactory
            containerProperties.clientId = clientId
            containerProperties.isMicrometerEnabled = true
            @Suppress("UNCHECKED_CAST")
            setRecordInterceptor(interceptor as RecordInterceptor<String, EventEnvelope<T>>)
            setCommonErrorHandler(errorHandler)
            containerProperties.ackMode = ackMode
            setConcurrency(concurrency)
            isBatchListener = false

            // ← NEW: enforce semantic type at the container level
            expectedEventType?.let {
                setRecordFilterStrategy(eventTypeFilter(expectedEventType))
                setAckDiscarded(ackDiscarded)
            }
        }

    @Bean("${Topics.PAYMENT_ORDER_CREATED}-factory")
    fun paymentOrderCreatedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderCreated>> {
        val cfg = cfgFor(EventMetadatas.PaymentOrderCreatedMetadata.topic, "${Topics.PAYMENT_ORDER_CREATED}-factory")
        return createTypedFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = EventMetadatas.PaymentOrderCreatedMetadata.eventType,
        )
    }

    @Bean("${Topics.PAYMENT_ORDER_PSP_CALL_REQUESTED}-factory")
    fun paymentOrderPspCallRequestedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderPspCallRequested>> {
        val cfg = cfgFor(
            EventMetadatas.PaymentOrderPspCallRequestedMetadata.topic,
            "${Topics.PAYMENT_ORDER_PSP_CALL_REQUESTED}-factory"
        )
        return createTypedFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = EventMetadatas.PaymentOrderPspCallRequestedMetadata.eventType,
        )
    }

    @Bean("${Topics.PAYMENT_STATUS_CHECK}-factory")
    fun paymentStatusCheckExecutorFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderStatusCheckRequested>> {
        val cfg = cfgFor(
            EventMetadatas.PaymentOrderStatusCheckScheduledMetadata.topic,
            "${Topics.PAYMENT_STATUS_CHECK}-factory"
        )
        return createTypedFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = EventMetadatas.PaymentOrderStatusCheckScheduledMetadata.eventType,
        )
    }

    @Bean
    fun mdcRecordInterceptor(): RecordInterceptor<String, EventEnvelope<*>> = HeaderMdcInterceptor()

    @Bean
    fun messageHandlerMethodFactory(): DefaultMessageHandlerMethodFactory = DefaultMessageHandlerMethodFactory()


    private fun eventTypeFilter(expected: String): RecordFilterStrategy<String, EventEnvelope<*>> =
        RecordFilterStrategy { rec: ConsumerRecord<String, EventEnvelope<*>> ->
            val ok = rec.value()?.eventType == expected
            if (!ok) {
                // Returning true => drop this record
                // (we’ll enable ackDiscarded so its offset is committed)
                true
            } else {
                false
            }
        }


    private fun cfgFor(topic: String, beanName: String): DynamicKafkaConsumersProperties.DynamicConsumer =
        dynamicProps.dynamicConsumers.firstOrNull { it.topic == topic }
            ?: error("Missing app.kafka.dynamic-consumers entry for topic '$topic' (required by bean '$beanName').")

}

class HeaderMdcInterceptor : RecordInterceptor<String, EventEnvelope<*>> {

    private val prevCtx = ThreadLocal<Map<String, String>?>()

    private fun putFrom(record: ConsumerRecord<String, EventEnvelope<*>>) {
        fun h(k: String) = record.headers().lastHeader(k)?.value()?.let { String(it) }

        // prefer headers; fall back to envelope
        MDC.put(GenericLogFields.TRACE_ID, h("traceId") ?: record.value().traceId)
        MDC.put(GenericLogFields.EVENT_ID, h("eventId") ?: record.value().eventId.toString())
        MDC.put(GenericLogFields.PARENT_EVENT_ID, h("parentEventId") ?: record.value().parentEventId?.toString())
        MDC.put(GenericLogFields.AGGREGATE_ID, record.value().aggregateId ?: record.key())
        MDC.put(GenericLogFields.EVENT_TYPE, h("eventType") ?: record.value().eventType)
    }

    override fun intercept(
        record: ConsumerRecord<String, EventEnvelope<*>>,
        consumer: Consumer<String, EventEnvelope<*>>
    ): ConsumerRecord<String, EventEnvelope<*>>? {
        prevCtx.set(MDC.getCopyOfContextMap())
        putFrom(record)
        return record // return null to skip; we’re not skipping here
    }


    override fun afterRecord(
        record: ConsumerRecord<String, EventEnvelope<*>>,
        consumer: Consumer<String, EventEnvelope<*>>
    ) {
        val prev = prevCtx.get()
        if (prev != null) MDC.setContextMap(prev) else MDC.clear()
        prevCtx.remove()
    }
}