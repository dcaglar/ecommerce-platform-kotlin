package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import java.time.LocalDateTime


interface PaymentOrderRepository {
    fun updateReturningIdempotent(paymentOrder: PaymentOrder): PaymentOrder?
     fun updateReturningIdempotentInitialCaptureRequest(paymentOrderId: Long,updatedAt: LocalDateTime): PaymentOrder?
        fun insertAll(orders: List<PaymentOrder>)
    fun countByPaymentId(paymentId: PaymentId): Long
    fun countByPaymentIdAndStatusIn(paymentId: PaymentId, statuses: List<String>): Long
    fun existsByPaymentIdAndStatus(paymentId: PaymentId, status: String): Boolean
    fun getMaxPaymentOrderId(): PaymentOrderId
    fun findByPaymentOrderId(paymentOrderId: PaymentOrderId): List<PaymentOrder>


}