package com.dogancaglar.paymentservice.web.dto

data class PaymentResponseDTO(
    val id: String,
    val buyerId: String,
    val orderId: String,
    val totalAmount: AmountDto,
    val status: String,                         // ✅ Added
    val createdAt: String,                      // ✅ Added
    val paymentOrders: List<PaymentOrderResponseDTO>
)