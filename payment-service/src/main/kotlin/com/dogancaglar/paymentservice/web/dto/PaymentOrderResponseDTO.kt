package com.dogancaglar.paymentservice.web.dto

data class PaymentOrderResponseDTO(
    val sellerId: String,
    val amount: AmountDto
)