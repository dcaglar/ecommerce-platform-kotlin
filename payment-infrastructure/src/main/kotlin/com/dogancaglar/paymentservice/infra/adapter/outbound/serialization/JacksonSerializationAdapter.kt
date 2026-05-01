package com.dogancaglar.paymentservice.infra.adapter.outbound.serialization

import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.dogancaglar.paymentservice.infra.adapter.outbound.serialization.JacksonUtil
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
class JacksonSerializationAdapter(
    @param:Qualifier("myObjectMapper") val objectMapper: ObjectMapper
) : SerializationPort {

    init {
        com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog.all.forEach {
            objectMapper.registerSubtypes(com.fasterxml.jackson.databind.jsontype.NamedType(it.clazz, it.eventType))
        }
    }

    override fun <T> toJson(value: T): String = objectMapper.writeValueAsString(value)

    override fun <T> fromJson(json: String, clazz: Class<T>): T =
        objectMapper.readValue(json, clazz)
}