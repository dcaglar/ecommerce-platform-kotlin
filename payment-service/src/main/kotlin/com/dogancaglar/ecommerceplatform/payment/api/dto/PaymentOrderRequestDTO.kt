package com.dogancaglar.ecommerceplatform.payment.api.dto

data class PaymentOrderRequestDTO(
    val sellerId: String,
    val amount: AmountDto
)