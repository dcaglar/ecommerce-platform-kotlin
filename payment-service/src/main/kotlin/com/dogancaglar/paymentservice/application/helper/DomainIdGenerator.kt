package com.dogancaglar.paymentservice.application.helper

import com.dogancaglar.paymentservice.adapter.redis.id.RedisIdGeneratorAdapter
import com.dogancaglar.paymentservice.domain.port.IdGeneratorPort
import com.dogancaglar.paymentservice.domain.port.IdWithPublicId
import org.springframework.stereotype.Component

@Component
class DomainIdGenerator(private val redisIdGeneratorAdapter: RedisIdGeneratorAdapter) : IdGeneratorPort {

    override fun nextPaymentId(): IdWithPublicId {
        val id = redisIdGeneratorAdapter.nextId("payment")
        return IdWithPublicId(id, "payment-$id")
    }

    override fun nextPaymentOrderId(): IdWithPublicId {
        val id = redisIdGeneratorAdapter.nextId("payment-order")
        return IdWithPublicId(id, "paymentorder-$id")
    }

    override fun nextId(namespace: String): Long {
        TODO("Not yet implemented")
    }

    override fun getRawValue(namespace: String): Long? {
        TODO("Not yet implemented")
    }

    override fun setMinValue(namespace: String, value: Long) {
        TODO("Not yet implemented")
    }


}