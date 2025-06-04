package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentOrderStatusCheckEntity
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrderStatusCheck
import java.time.LocalDateTime

interface PaymentOrderStatusCheckOutBoundPort {
    fun save(paymentOrderStatusCheck: PaymentOrderStatusCheck)
    fun findDueStatusChecks(now: LocalDateTime): List<PaymentOrderStatusCheck>
    fun markAsProcessed(id: Long)
}