import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.config.KafkaProperties
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.fasterxml.jackson.core.type.TypeReference
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.stereotype.Component


@Configuration
@EnableConfigurationProperties(KafkaProperties::class)
class KafkaConsumerConfig(
    private val kafkaProperties: KafkaProperties
) {

    private fun getCommonKafkaProps(groupIdKey: String): Map<String, Any> {
        val groupId = when (groupIdKey) {
            "payment-order-created" -> kafkaProperties.consumer.groups.paymentExecutor
            "payment-order-retry-requested" -> kafkaProperties.consumer.groups.paymentRetryExecutor
            else -> throw IllegalArgumentException("Unknown group key: $groupIdKey")
        }

        return mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            JsonDeserializer.TRUSTED_PACKAGES to kafkaProperties.trustedPackages
        )
    }

    private fun <T> createConsumerFactory(
        groupIdKey: String,
        valueType: Class<T>
    ): ConsumerFactory<String, EventEnvelope<T>> {
        val props = getCommonKafkaProps(groupIdKey)

        val typeRef = object : TypeReference<EventEnvelope<T>>() {}
        val deserializer = JsonDeserializer<EventEnvelope<T>>(typeRef)

        deserializer.setRemoveTypeHeaders(false)
        deserializer.setUseTypeMapperForKey(true)
        deserializer.setUseTypeHeaders(true)
        deserializer.addTrustedPackages(kafkaProperties.trustedPackages)

        return DefaultKafkaConsumerFactory(props, StringDeserializer(), deserializer)
    }

    private fun <T> listenerContainerFactory(
        groupIdKey: String,
        valueType: Class<T>
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>>()
        factory.consumerFactory = createConsumerFactory(groupIdKey, valueType)
        return factory
    }

    @Bean
    fun paymentOrderCreatedKafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderCreated>> {
        return listenerContainerFactory(
            groupIdKey = "payment-order-created",
            valueType = PaymentOrderCreated::class.java
        )
    }
}
