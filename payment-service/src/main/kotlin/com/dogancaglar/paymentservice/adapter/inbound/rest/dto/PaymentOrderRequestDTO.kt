package com.dogancaglar.paymentservice.adapter.inbound.rest.dto

import com.dogancaglar.port.out.web.dto.AmountDto
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull


data class PaymentOrderRequestDTO(
    @field:NotBlank
    val sellerId: String,
    @field:NotNull
    @field:Valid
    val amount: AmountDto
)
