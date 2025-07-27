package com.dogancaglar.com.dogancaglar.payment.application.port.out

interface SerializationPort {
    fun <T> toJson(value: T): String

}