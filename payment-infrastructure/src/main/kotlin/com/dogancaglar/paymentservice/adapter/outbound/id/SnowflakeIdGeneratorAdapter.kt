// src/main/kotlin/.../id/SnowflakeIdGeneratorAdapter.kt
package com.dogancaglar.paymentservice.adapter.outbound.id

import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.snowflake.IdGenerationProperties
import com.dogancaglar.paymentservice.snowflake.SnowflakeCore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SnowflakeIdGeneratorAdapter(
    props: IdGenerationProperties,
    @Value("\${app.instance-id}") instanceId: String
) : IdGeneratorPort {

    private val core = SnowflakeCore(
        epochMillis = props.epochMillis,
        regionId = props.regionId
    )

    private val nodeId = (instanceId.substringAfterLast("-").toIntOrNull() ?: instanceId.hashCode()).let { 
        (it and Int.MAX_VALUE) % 32 
    }

    override fun nextPaymentIntentId(): Long {
        return core.nextId(nodeId)
    }

    override fun nextPaymentId(): Long {
        return core.nextId(nodeId)
    }

    override fun nextPaymentOrderId(): Long {
        return core.nextId(nodeId)
    }
}