package com.dogancaglar.paymentservice.config.messaging

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.fasterxml.jackson.core.type.TypeReference
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EventMetadataConfig {

    @Bean
    fun topicTypeMap(): Map<String, TypeReference<EventEnvelope<*>>> {
        return EventMetadatas.all.associate { metadata ->
            @Suppress("UNCHECKED_CAST")
            metadata.topic to (metadata.typeRef as TypeReference<EventEnvelope<*>>)
        }
    }
}