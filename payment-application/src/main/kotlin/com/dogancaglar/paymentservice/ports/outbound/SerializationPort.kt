package com.dogancaglar.paymentservice.ports.outbound

interface SerializationPort {
    fun <T> toJson(value: T): String

}