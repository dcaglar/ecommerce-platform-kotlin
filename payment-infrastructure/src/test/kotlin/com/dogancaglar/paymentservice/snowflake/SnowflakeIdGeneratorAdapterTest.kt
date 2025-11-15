package com.dogancaglar.paymentservice.snowflake

import com.dogancaglar.paymentservice.adapter.outbound.id.SnowflakeIdGeneratorAdapter
import com.dogancaglar.paymentservice.domain.model.vo.*
import com.dogancaglar.paymentservice.snowflake.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SnowflakeIdGeneratorAdapterTest {

    private lateinit var props: IdGenerationProperties
    private lateinit var strategies: ShardStrategies
    private lateinit var adapter: SnowflakeIdGeneratorAdapter

    @BeforeEach
    fun setup() {
        props = IdGenerationProperties(
            epochMillis = 0,
            regionId = 1,
            numCoordShards = 32,
            numSellerShards = 32
        )

        // Override strategies so shard computation is deterministic
        strategies = mockk(relaxed = true)
        every { strategies.coordShard(any(), any()) } returns 7     // nodeId = 7
        every { strategies.sellerShard(any()) } returns 13          // nodeId = 13

        val core = object : SnowflakeCore(epochMillis = 0, regionId = 1) {
            private var counter = 1L
            override fun nextId(nodeId: Int): Long {
                return (nodeId.toLong() shl 10) + counter++    // visible shard marker
            }
        }

        adapter = SnowflakeIdGeneratorAdapter(props).apply {
            // Replace internals with mocks
            val strategiesField = this::class.java.getDeclaredField("strategies")
            strategiesField.isAccessible = true
            strategiesField.set(this, strategies)

            val coreField = this::class.java.getDeclaredField("core")
            coreField.isAccessible = true
            coreField.set(this, core)
        }
    }

    @Test
    fun `nextPaymentId uses coord shard and returns snowflake`() {
        val id = adapter.nextPaymentId(
            buyerId = BuyerId("buyer-x"),
            orderId = OrderId("order-y")
        )

        // Assert shard was used (shifted left inside fake core)
        val shard = id shr 10
        assertEquals(7, shard)
    }

    @Test
    fun `nextPaymentOrderId uses seller shard and returns snowflake`() {
        val id = adapter.nextPaymentOrderId(
            sellerId = SellerId("seller-999")
        )

        val shard = id shr 10
        assertEquals(13, shard)
    }

    @Test
    fun `invalid coord shard throws error`() {
        every { strategies.coordShard(any(), any()) } returns 99 // invalid

        val ex = assertThrows(IllegalArgumentException::class.java) {
            adapter.nextPaymentId(BuyerId("b"), OrderId("o"))
        }
        assertTrue(ex.message!!.contains("coordShard must be in 0..31"))
    }

    @Test
    fun `invalid seller shard throws error`() {
        every { strategies.sellerShard(any()) } returns 99

        val ex = assertThrows(IllegalArgumentException::class.java) {
            adapter.nextPaymentOrderId(SellerId("s1"))
        }
        assertTrue(ex.message!!.contains("sellerShard must be in 0..31"))
    }
}