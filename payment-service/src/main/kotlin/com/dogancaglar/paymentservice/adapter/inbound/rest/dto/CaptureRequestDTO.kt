package com.dogancaglar.paymentservice.adapter.inbound.rest.dto

import com.dogancaglar.port.out.web.dto.AmountDto
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class CaptureRequestDTO(
    @field:NotBlank
    val merchantAccountId: String,
    @field:NotNull
    @field:Valid
    val amount: AmountDto,
)