package com.dogancaglar.paymentservice.snowflake

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payments.id")
data class IdGenerationProperties(
    /**
     * Custom epoch in millis, e.g. 2025-01-01T00:00:00Z.
     */
    val epochMillis: Long = 1735689600000L, // adjust if you like
    /**
     * Region / DC id (0..31), e.g. EU1=1, US1=2...
     */
    val regionId: Int = 1,
    /**
     * Number of logical Payment coordination shards, e.g. 8.
     */
    val numCoordShards: Int = 8,
    /**
     * Number of seller shards for PaymentOrder + Ledger, e.g. 32.
     */
    val numSellerShards: Int = 32
)