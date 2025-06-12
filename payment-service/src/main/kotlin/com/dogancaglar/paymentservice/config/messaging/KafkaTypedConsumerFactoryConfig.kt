package com.dogancaglar.paymentservice.config.messaging

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.application.event.PaymentOrderSucceeded
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.MDC
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.MicrometerConsumerListener
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory
import java.util.*

@Configuration
@EnableConfigurationProperties(KafkaProperties::class)
class KafkaTypedConsumerFactoryConfig(
    private val consumerProps: KafkaProperties,
    private val meterRegistry: MeterRegistry,
) {

    @Bean("custom-kafka-consumer-factory-for-micrometer")
    fun defaultKafkaConsumerFactory(): DefaultKafkaConsumerFactory<String, EventEnvelope<*>> {
        val cf = DefaultKafkaConsumerFactory<String, EventEnvelope<*>>(consumerProps.toMap())
        // 🔥 Enables collection of native kafka.consumer.* metrics
        cf.addListener(MicrometerConsumerListener(meterRegistry))
        return cf
    }

    @Bean
    fun mdcRecordInterceptor(): RecordInterceptor<String, EventEnvelope<*>> = RecordInterceptor { record, _ ->
        fun header(key: String) = record.headers().lastHeader(key)?.value()?.let { String(it) }
        listOf(LogFields.TRACE_ID, LogFields.EVENT_ID, LogFields.PARENT_EVENT_ID).forEach { key ->
            header(key)?.let { MDC.put(key, it) }
        }
        MDC.put(LogFields.AGGREGATE_ID, record.value()?.aggregateId ?: record.key())
        record
    }

    @Bean("payment_order_created_queue-factory")
    fun paymentOrderCreatedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>
    )
            : ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderCreated>> =
        createTypedFactory(
            clientId = EventMetadatas.PaymentOrderCreatedMetadata.eventType,
            valueType = PaymentOrderCreated::class.java,
            interceptor = interceptor,
            defaultKafkaConsumerFactory = customFactory
        )

    @Bean("payment_order_retry_request_topic-factory")
    fun paymentRetryRequestedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>
    )
            : ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderRetryRequested>> =
        createTypedFactory(
            clientId = EventMetadatas.PaymentOrderRetryRequestedMetadata.eventType,
            valueType = PaymentOrderRetryRequested::class.java,
            interceptor = interceptor,
            defaultKafkaConsumerFactory = customFactory
        )

    @Bean("payment_status_check_scheduler_topic-factory")
    fun paymentStatusCheckExecutorFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>
    )
            : ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderStatusCheckRequested>> =
        createTypedFactory(
            EventMetadatas.PaymentOrderStatusCheckScheduledMetadata.eventType,
            PaymentOrderStatusCheckRequested::class.java,
            interceptor = interceptor,
            defaultKafkaConsumerFactory = customFactory
        )

    @Bean("payment_order_succeded_topic-factory")
    fun paymentOrderSucceededFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>
    )
            : ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderSucceeded>> =
        createTypedFactory(
            clientId = EventMetadatas.PaymentOrderSuccededMetaData.eventType,
            valueType = PaymentOrderSucceeded::class.java,
            interceptor = interceptor,
            defaultKafkaConsumerFactory = customFactory
        )

    /* ---------- helper ---------- */

    private fun <T : Any> createTypedFactory(
        clientId: String,
        valueType: Class<T>,
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        defaultKafkaConsumerFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>> {

        // 1. Create a fresh listener‐container factory
        val factory = ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>>()

        // 2. Reuse your shared consumer factory
        @Suppress("UNCHECKED_CAST")
        factory.consumerFactory = defaultKafkaConsumerFactory

        // 3. Set the client.id so Micrometer tags the client metrics correctly
        factory.containerProperties.clientId = clientId

        // 4. Enable Micrometer’s built-in metrics (listener timers + ConsumerMetrics)
        factory.containerProperties.isMicrometerEnabled = true

        // 5. Attach your MDC interceptor for propagating trace headers
        @Suppress("UNCHECKED_CAST")
        factory.setRecordInterceptor(
            interceptor as RecordInterceptor<String, EventEnvelope<T>>
        )

        return factory
    }

    @Bean
    fun timeZoneConfigurer(): CommandLineRunner = CommandLineRunner {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
    }

    @Configuration
    class MessageHandlerFactoryConfig {
        @Bean
        fun messageHandlerMethodFactory() = DefaultMessageHandlerMethodFactory().apply {
            setMessageConverter(MappingJackson2MessageConverter())
        }
    }
}