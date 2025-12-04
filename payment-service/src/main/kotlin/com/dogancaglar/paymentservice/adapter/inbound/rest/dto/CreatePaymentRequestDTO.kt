package com.dogancaglar.paymentservice.adapter.inbound.rest.dto

import com.dogancaglar.port.out.web.dto.AmountDto
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class CreatePaymentRequestDTO(
    @field:NotBlank
    val buyerId: String,
    @field:NotNull
    @field:Valid
    val totalAmount: AmountDto,
    @field:NotEmpty
    val orderId: String,
    @field:NotEmpty
    @field:Valid
    val paymentOrders: List<PaymentLineDTO>
)