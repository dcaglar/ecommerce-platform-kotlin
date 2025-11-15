package com.dogancaglar.paymentservice.snowflake

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

class PaymentOrderIdShardEncodingTest {

    private val props = IdGenerationProperties(
        numCoordShards = 8,
        numSellerShards = 32
    )
    private val strategies = ShardStrategies(props)
    private val core = SnowflakeCore(props.epochMillis, props.regionId)

    @Test
    fun `sellerShard must appear as nodeId in PaymentOrderId`() {
        val sellerId = "SELLER-222"
        val shard = strategies.sellerShard(sellerId)

        val orderId = core.nextId(shard)
        val extracted = core.extractNodeId(orderId)

        assertEquals(shard, extracted, "PaymentOrderId nodeId must equal sellerShard")
    }

    @Test
    fun `coordShard must appear as nodeId in PaymentId`() {
        val buyerId = "BUYER-123"
        val orderId = "ORDER-XYZ"

        val shard = strategies.coordShard(buyerId, orderId)
        val paymentId = core.nextId(shard)

        val extracted = core.extractNodeId(paymentId)

        assertEquals(shard, extracted, "PaymentId nodeId must equal coordShard")
    }


}