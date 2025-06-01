package com.dogancaglar.paymentservice.config.messaging

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.*
import com.dogancaglar.paymentservice.config.serialization.EventEnvelopeDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.MDC
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory
import java.util.*
import java.util.function.Supplier

@Configuration
@EnableConfigurationProperties(KafkaProperties::class)
class KafkaTypedConsumerFactoryConfig(
    private val kafkaProperties: KafkaProperties
) {
    /* ---------- interceptor bean shared by all listener factories ---------- */

    /**
     * Copies correlation headers (traceId / eventId / parentEventId) into
     * the listener thread’s MDC so that *framework* logs are correlated
     * even before your handler enters LogContext.with.
     */
    @Bean
    fun mdcRecordInterceptor(): RecordInterceptor<String, EventEnvelope<*>> =
        RecordInterceptor { record, _ ->
            fun header(k: String) = record.headers().lastHeader(k)?.value()?.let { String(it) }
            header(LogFields.TRACE_ID)?.let { MDC.put(LogFields.TRACE_ID, it) }
            header(LogFields.EVENT_ID)?.let { MDC.put(LogFields.EVENT_ID, it) }
            header(LogFields.PARENT_EVENT_ID)?.let { MDC.put(LogFields.PARENT_EVENT_ID, it) }
            MDC.put(LogFields.AGGREGATE_ID, record.value()?.aggregateId ?: record.key())
            record
        }

    /* ---------- typed factories ---------- */

    @Bean("payment_order_created_queue-factory")
    fun paymentOrderCreatedFactory(interceptor: RecordInterceptor<String, EventEnvelope<*>>)
            : ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderCreated>> =
        createTypedFactory(PaymentOrderCreated::class.java, interceptor)

    @Bean("payment_order_retry_request_topic-factory")
    fun paymentRetryRequestedFactory(interceptor: RecordInterceptor<String, EventEnvelope<*>>)
            : ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderRetryRequested>> =
        createTypedFactory(PaymentOrderRetryRequested::class.java, interceptor)

    @Bean("payment_status_check_scheduler_topic-factory")
    fun paymentScheduledStatusFactory(interceptor: RecordInterceptor<String, EventEnvelope<*>>)
            : ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderStatusScheduled>> =
        createTypedFactory(PaymentOrderStatusScheduled::class.java, interceptor)

    @Bean("due_payment_status_check_topic-factory")
    fun paymentStatusCheckExecutorFactory(interceptor: RecordInterceptor<String, EventEnvelope<*>>)
            : ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<DuePaymentOrderStatusCheck>> =
        createTypedFactory(DuePaymentOrderStatusCheck::class.java, interceptor)

    @Bean("payment_order_succeded_topic-factory")
    fun paymentOrderSucceededFactory(interceptor: RecordInterceptor<String, EventEnvelope<*>>)
            : ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderSucceeded>> =
        createTypedFactory(PaymentOrderSucceeded::class.java, interceptor)

    /* ---------- helper ---------- */

    private fun <T : Any> createTypedFactory(
        valueType: Class<T>,
        interceptor: RecordInterceptor<String, EventEnvelope<*>>
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>> {

        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to EventEnvelopeDeserializer::class.java
        )

        val factory = ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>>()
        factory.consumerFactory = DefaultKafkaConsumerFactory(
            props,
            Supplier { StringDeserializer() },
            Supplier { EventEnvelopeDeserializer() as Deserializer<EventEnvelope<T>> }
        )
        factory.setRecordInterceptor(interceptor as RecordInterceptor<String, EventEnvelope<T>>)
        return factory
    }

    /* ---------- misc beans ---------- */

    @Bean
    fun timeZoneConfigurer(): CommandLineRunner = CommandLineRunner {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
    }

    @Configuration
    class MessageHandlerFactoryConfig {
        @Bean
        fun messageHandlerMethodFactory(): DefaultMessageHandlerMethodFactory =
            DefaultMessageHandlerMethodFactory().apply {
                setMessageConverter(MappingJackson2MessageConverter())
            }
    }
}