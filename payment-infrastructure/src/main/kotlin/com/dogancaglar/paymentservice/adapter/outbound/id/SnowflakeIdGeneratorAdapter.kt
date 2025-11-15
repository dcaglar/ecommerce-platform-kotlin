// src/main/kotlin/.../id/SnowflakeIdGeneratorAdapter.kt
package com.dogancaglar.paymentservice.adapter.outbound.id

import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.snowflake.IdGenerationProperties
import com.dogancaglar.paymentservice.snowflake.ShardStrategies
import com.dogancaglar.paymentservice.snowflake.SnowflakeCore
import org.springframework.stereotype.Component

@Component
class SnowflakeIdGeneratorAdapter(
    props: IdGenerationProperties
) : IdGeneratorPort {

    private val strategies = ShardStrategies(props)
    private val core = SnowflakeCore(
        epochMillis = props.epochMillis,
        regionId = props.regionId
    )

    private val numCoordShards = props.numCoordShards
    private val numSellerShards = props.numSellerShards

    override fun nextPaymentId(buyerId: BuyerId, orderId: OrderId): Long {
        val shard = strategies.coordShard(buyerId.value, orderId.value)
        require(shard in 0..31) {
            "coordShard must be in 0..31 for Snowflake nodeId; was $shard (numCoordShards=$numCoordShards)"
        }
        return core.nextId(shard) // nodeId == coordShard
    }

    override fun nextPaymentOrderId(sellerId: SellerId): Long {
        val shard = strategies.sellerShard(sellerId.value)
        require(shard in 0..31) {
            "sellerShard must be in 0..31 for Snowflake nodeId; was $shard (numSellerShards=$numSellerShards)"
        }
        return core.nextId(shard) // nodeId == sellerShard
    }
}