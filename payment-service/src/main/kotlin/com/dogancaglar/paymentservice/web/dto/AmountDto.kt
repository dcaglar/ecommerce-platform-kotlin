package com.dogancaglar.paymentservice.web.dto

import java.math.BigDecimal

data class AmountDto(
    val value: BigDecimal,
    val currency: String
)