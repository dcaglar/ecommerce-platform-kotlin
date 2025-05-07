package com.dogancaglar.paymentservice.web.dto


data class PaymentOrderRequestDTO(
    val sellerId: String,
    val amount: AmountDto
)
