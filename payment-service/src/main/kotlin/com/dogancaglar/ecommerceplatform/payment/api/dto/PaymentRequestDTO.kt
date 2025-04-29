package com.dogancaglar.ecommerceplatform.payment.api.dto

data class PaymentRequestDTO(
    val buyerId: String,
    val totalAmount: AmountDto,
    val orderId: String,
    val paymentOrders: List<PaymentOrderRequestDTO>
)