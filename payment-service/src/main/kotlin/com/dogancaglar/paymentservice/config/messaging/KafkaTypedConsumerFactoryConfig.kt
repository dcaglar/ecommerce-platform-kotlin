package com.dogancaglar.paymentservice.config.messaging

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.config.serialization.EventEnvelopeDeserializer
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusCheckRequested
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory
import java.util.function.Supplier

@Configuration
@EnableConfigurationProperties(KafkaProperties::class)
class KafkaTypedConsumerFactoryConfig(
    private val kafkaProperties: KafkaProperties
) {

    @Bean("payment_order_created-factory")
    fun paymentOrderCreatedFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderCreated>> {
        return createTypedFactory(PaymentOrderCreated::class.java)
    }

    @Bean("payment_order_retry_request_topic-factory")
    fun paymentRetryRequestedFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderRetryRequested>> {
        return createTypedFactory(PaymentOrderRetryRequested::class.java)
    }

    @Bean("scheduled_status_check-factory")
    fun paymentScheduledStatusFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderStatusCheckRequested>> {
        return createTypedFactory(PaymentOrderStatusCheckRequested::class.java)
    }
    @Bean("delay_scheduling_topic-factory")
    fun paymentStatusScheduledFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderStatusCheckRequested>> {
        return createTypedFactory(PaymentOrderStatusCheckRequested::class.java)
    }

    @Bean("payment_status_check-factory")
    fun paymenttatuscCheckFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderStatusCheckRequested>> {
        return createTypedFactory(PaymentOrderStatusCheckRequested::class.java)
    }

    private fun <T : Any> createTypedFactory(valueType: Class<T>): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>> {
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
        return factory
    }
    @Configuration
    class MessageHandlerFactoryConfig {

        @Bean
        fun messageHandlerMethodFactory(): DefaultMessageHandlerMethodFactory {
            val factory = DefaultMessageHandlerMethodFactory()
            factory.setMessageConverter(MappingJackson2MessageConverter())
            return factory
        }
    }
}