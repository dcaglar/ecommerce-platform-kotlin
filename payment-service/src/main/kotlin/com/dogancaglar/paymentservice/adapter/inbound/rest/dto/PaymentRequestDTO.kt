package com.dogancaglar.port.out.web.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class PaymentRequestDTO(
    @field:NotBlank
    val buyerId: String,
    @field:NotNull
    @field:Valid
    val totalAmount: AmountDto,
    @field:NotEmpty
    val orderId: String,
    @field:NotEmpty
    @field:Valid
    val paymentOrders: List<PaymentOrderRequestDTO>
)