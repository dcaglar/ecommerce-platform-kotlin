package com.dogancaglar.port.out.web.dto

data class PaymentOrderResponseDTO(
    val sellerId: String,
    val amount: AmountDto,
    val paymentOrderId:String
)