package com.dogancaglar.paymentservice.domain.port

interface IdGenerator {
    fun nextId(namespace: String): Long
    fun nextPublicId(prefix: String): String
    fun getRawValue(namespace: String): Long?
    fun setMinValue(namespace: String, value: Long)
}