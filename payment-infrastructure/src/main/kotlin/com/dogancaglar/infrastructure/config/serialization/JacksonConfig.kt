package com.dogancaglar.infrastructure.config.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean("myObjectMapper")
    fun objectMapper(): ObjectMapper {
        val objectMapper = ObjectMapper()
            .registerModule(JavaTimeModule()).registerKotlinModule()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        logger.info("📦 Objectmapper config using ObjectMapper: $objectMapper")
        return objectMapper
    }
}