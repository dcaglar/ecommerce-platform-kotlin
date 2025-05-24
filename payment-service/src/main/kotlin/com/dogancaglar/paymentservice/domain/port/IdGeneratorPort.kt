package com.dogancaglar.paymentservice.domain.port

interface IdGeneratorPort {
    fun nextPaymentId(): Long
    fun nextPaymentOrderId(): Long
    fun nextPublicId(prefix: String): String
    fun getRawValue(namespace: String): Long?
    fun setMinValue(namespace: String, value: Long)
}

