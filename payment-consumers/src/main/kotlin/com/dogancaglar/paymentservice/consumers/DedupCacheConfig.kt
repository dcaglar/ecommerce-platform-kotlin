package com.dogancaglar.paymentservice.consumers

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DedupCacheConfig {

    @Bean
    fun eventDedupCache(): EventDedupCache = EventDedupCache()
}