// KafkaDynamicConsumerRegistrar.kt
package com.dogancaglar.paymentservice.config.messaging

import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaListenerConfigurer
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar
import org.springframework.kafka.config.MethodKafkaListenerEndpoint
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory


@Configuration
class KafkaDynamicConsumerRegistrar(
    private val dynamicProps: DynamicKafkaConsumersProperties,
    private val context: ApplicationContext
) : KafkaListenerConfigurer {

    override fun configureKafkaListeners(registrar: KafkaListenerEndpointRegistrar) {
        val methodFactory = context.getBean(DefaultMessageHandlerMethodFactory::class.java)

        dynamicProps.dynamicConsumers.forEach { consumer ->
            val clazz = Class.forName(consumer.className)
            val bean = context.getBean(clazz)
            val method = clazz.methods.firstOrNull { it.name == "handle" }
                ?: throw IllegalStateException("No handle() method in ${consumer.className}")

            val factory = context.getBean("${consumer.topic}-factory")
                    as ConcurrentKafkaListenerContainerFactory<String, *>

            MethodKafkaListenerEndpoint<String, Any>().apply {
                setId(consumer.id)
                setGroupId(consumer.groupId)
                setBean(bean)
                setMethod(method)
                setTopics(consumer.topic)
                setMessageHandlerMethodFactory(methodFactory)
            }.also { registrar.registerEndpoint(it, factory) }
        }
    }
}