package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import java.time.LocalDateTime


interface PaymentOutboundPort {
    fun save(payment: Payment)
    fun findByPaymentId(id: Long): Payment?

}


data class Payment(
    val paymentId: Long,                       // Optional before persistence
    val paymentPublicId: String,                       // Optional before persistence
    val buyerId: String,                   // Who is paying
    val orderId: String,                   // Order being paid for
    val totalAmount: Amount,               // Amount object with value + currency
    val status: PaymentStatus,             // INITIATED, SUCCESS, FAILED
    val createdAt: LocalDateTime,          // Timestamp of creation
    val paymentOrders: List<PaymentOrder>  // Breakdown per seller
)
