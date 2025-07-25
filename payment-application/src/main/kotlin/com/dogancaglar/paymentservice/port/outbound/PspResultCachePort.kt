package com.dogancaglar.paymentservice.port.outbound

import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId


interface PspResultCachePort {
    fun put(pspKey: PaymentOrderId, resultJson: String)
    fun get(pspKey: PaymentOrderId): String?
    fun remove(pspKey: PaymentOrderId)
}

