package com.dogancaglar.paymentservice.domain.port

interface IdGeneratorPort {


    fun nextId(namespace: String): Long


    fun getRawValue(namespace: String): Long?


    fun setMinValue(namespace: String, value: Long)


}

