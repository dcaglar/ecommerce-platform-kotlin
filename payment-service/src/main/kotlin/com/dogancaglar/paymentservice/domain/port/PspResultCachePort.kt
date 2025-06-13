package com.dogancaglar.paymentservice.domain.port

interface PspResultCachePort {
    fun put(pspKey: String, resultJson: String)
    fun get(pspKey: String): String?
    fun remove(pspKey: String)
}

