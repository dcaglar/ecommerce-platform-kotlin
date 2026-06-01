package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.application.service.IdempotencyService
import com.dogancaglar.paymentservice.infra.adapter.outbound.hash.CanonicalJsonHasher
import com.dogancaglar.paymentservice.ports.outbound.HasherPort
import com.dogancaglar.paymentservice.ports.outbound.IdempotencyStorePort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
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



    @Bean
    fun idempotencyService(
        store: IdempotencyStorePort,
        hasher: HasherPort,
        serializer: SerializationPort
    ): IdempotencyService {
        return IdempotencyService(store, hasher, serializer)
    }
}