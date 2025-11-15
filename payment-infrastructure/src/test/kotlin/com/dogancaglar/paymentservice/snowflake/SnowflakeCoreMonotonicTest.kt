package com.dogancaglar.paymentservice.snowflake

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue

class SnowflakeCoreMonotonicTest {

    private val props = IdGenerationProperties(
        epochMillis = 1735689600000L, // 2025-01-01
        regionId = 1,
        numCoordShards = 8,
        numSellerShards = 32
    )

    @Test
    fun `snowflake IDs must increase for the same shard`() {
        val core = SnowflakeCore(props.epochMillis, props.regionId)
        val shardId = 3

        val id1 = core.nextId(shardId)
        print("id1:$id1")
        val id2 = core.nextId(shardId)
        print("id2:$id2")
        val id3 = core.nextId(shardId)
        print("id3:$id3")

        assertTrue(id1 < id2, "Expected id1 < id2 but got $id1 >= $id2")
        assertTrue(id2 < id3, "Expected id2 < id3 but got $id2 >= $id3")
    }
}