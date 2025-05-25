package com.dogancaglar.paymentservice.domain.port

interface IdGeneratorPort {
    fun nextPaymentId(): IdWithPublicId

    fun nextPaymentOrderId(): IdWithPublicId

    fun nextId(namespace: String): Long
    fun getRawValue(namespace: String): Long?
    fun setMinValue(namespace: String, value: Long)


}


data class IdWithPublicId(
    val id: Long,
    val publicId: String
)

