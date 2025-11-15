package com.dogancaglar.paymentservice.snowflake

class FakeSnowflakeCore : SnowflakeCore(epochMillis = 0, regionId = 0) {
    private var counter = 1000L
    override fun nextId(nodeId: Int): Long {
        return counter++ + (nodeId.toLong() shl 5)  // so we can assert exact shard/nodeId
    }
}