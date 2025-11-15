package com.dogancaglar.paymentservice.adapter.outbound.routing

import com.dogancaglar.paymentservice.ports.outbound.ShardRoutingPort
import com.dogancaglar.paymentservice.snowflake.IdGenerationProperties
import com.dogancaglar.paymentservice.snowflake.ShardStrategies
import com.dogancaglar.paymentservice.snowflake.SnowflakeCore
import org.springframework.stereotype.Component

@Component
class DefaultShardRouter(
    props: IdGenerationProperties
) : ShardRoutingPort {

    private val strategies = ShardStrategies(props)
    private val core = SnowflakeCore(props.epochMillis, props.regionId)

    private val numCoordShards = props.numCoordShards
    private val numSellerShards = props.numSellerShards

    override fun paymentDbShard(paymentId: Long): Int =
        core.extractNodeId(paymentId) % numCoordShards

    override fun paymentOrderDbShard(paymentOrderId: Long): Int =
        core.extractNodeId(paymentOrderId) % numSellerShards

    override fun ledgerDbShard(sellerId: String): Int =
        strategies.sellerShard(sellerId)

    override fun paymentKafkaPartition(paymentOrderId: Long, numPartitions: Int): Int =
        core.extractNodeId(paymentOrderId) % numPartitions

    override fun ledgerKafkaPartition(sellerId: String, numPartitions: Int): Int =
        (sellerId.hashCode() and Int.MAX_VALUE) % numPartitions
}