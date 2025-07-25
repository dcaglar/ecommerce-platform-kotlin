package com.dogancaglar.port.out.adapter.serialization

import com.dogancaglar.com.dogancaglar.payment.application.port.out.SerializationPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class JacksonSerializationAdapter(
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) : SerializationPort {
    override fun <T> toJson(value: T): String = objectMapper.writeValueAsString(value)
}