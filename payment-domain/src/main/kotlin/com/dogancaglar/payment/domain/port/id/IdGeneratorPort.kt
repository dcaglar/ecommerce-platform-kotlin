package com.dogancaglar.payment.domain.port.id

interface IdGeneratorPort {


    fun nextId(namespace: String): Long


    fun getRawValue(namespace: String): Long?


    fun setMinValue(namespace: String, value: Long)


}