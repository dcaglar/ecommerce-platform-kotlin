// src/main/kotlin/.../id/ShardStrategies.kt
package com.dogancaglar.paymentservice.snowflake


class ShardStrategies(
    private val props: IdGenerationProperties
) {
    fun coordShard(buyerId: String, orderId: String): Int {
        val key = "$buyerId:$orderId"
        val hash = key.hashCode() and Int.MAX_VALUE
        return hash % props.numCoordShards
    }

    fun sellerShard(sellerId: String): Int {
        val hash = sellerId.hashCode() and Int.MAX_VALUE
        return hash % props.numSellerShards
    }
}