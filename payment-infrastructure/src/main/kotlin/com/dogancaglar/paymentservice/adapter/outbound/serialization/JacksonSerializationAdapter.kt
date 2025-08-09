package com.dogancaglar.paymentservice.adapter.outbound.serialization

import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.dogancaglar.paymentservice.serialization.JacksonUtil
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class JacksonSerializationAdapter(
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) : SerializationPort {
    override fun <T> toJson(value: T): String = objectMapper.writeValueAsString(value)
}


@Configuration
class JacksonConfig {
    @Bean("myObjectMapper")
    fun objectMapper(): ObjectMapper = JacksonUtil.createObjectMapper()
}


@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}