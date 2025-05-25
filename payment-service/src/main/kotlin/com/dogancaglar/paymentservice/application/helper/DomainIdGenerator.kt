package com.dogancaglar.paymentservice.application.helper

import com.dogancaglar.paymentservice.domain.port.IdGeneratorPort
import org.springframework.stereotype.Component

@Component
class DomainIdGenerator(
    private val idGenerator: IdGeneratorPort
) {

    fun nextPaymentId(): IdWithPublicId {
        val id = idGenerator.nextId("payment")
        return IdWithPublicId(id, "payment-$id")
    }

    fun nextPaymentOrderId(): IdWithPublicId {
        val id = idGenerator.nextId("payment-order")
        return IdWithPublicId(id, "paymentorder-$id")
    }

    data class IdWithPublicId(
        val id: Long,
        val publicId: String
    )
}