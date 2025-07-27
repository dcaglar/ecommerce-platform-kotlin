package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatusCheck
import java.time.LocalDateTime

interface PaymentOrderStatusCheckRepository {
    fun save(paymentOrderStatusCheck: PaymentOrderStatusCheck)
    fun findDueStatusChecks(now: LocalDateTime): List<PaymentOrderStatusCheck>
    fun markAsProcessed(id: Long)
}