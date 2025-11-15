package com.dogancaglar.paymentservice.snowflake

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue


class FullFlowIdGenerationTest {

    private val props = IdGenerationProperties(
        regionId = 1,
        numCoordShards = 8,
        numSellerShards = 32
    )

    private val strategies = ShardStrategies(props)
    private val core = SnowflakeCore(props.epochMillis, props.regionId)

    @Test
    fun `full flow - PaymentId and PaymentOrderIds`() {
        val buyerId = "BUYER-123"
        val orderId = "ORDER-20240508-XYZ"

        // 1. PaymentId
        val coordShard = strategies.coordShard(buyerId, orderId)
        val paymentId = core.nextId(coordShard)

        assertEquals(coordShard, core.extractNodeId(paymentId))
        assertEquals(1, core.extractRegionId(paymentId))

        // 2. PaymentOrders
        val sellers = listOf("SELLER-111", "SELLER-222", "SELLER-333")

        sellers.forEach { sellerId ->
            val sellerShard = strategies.sellerShard(sellerId)
            val poId = core.nextId(sellerShard)

            assertEquals(
                sellerShard,
                core.extractNodeId(poId)
            )

            // Sanity check - ordering
            Thread.sleep(1)
            val poId2 = core.nextId(sellerShard)
            assertTrue(poId < poId2, "IDs must be monotonic for seller=$sellerId")
        }
    }
}