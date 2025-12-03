// payment-infrastructure/src/main/kotlin/.../config/IdempotencyConfig.kt
package com.dogancaglar.paymentservice.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class IdempotencyConfig {

    @Bean
    fun canonicalJsonHasher(objectMapper: ObjectMapper): CanonicalJsonHasher {
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        return CanonicalJsonHasher(objectMapper)
    }
}