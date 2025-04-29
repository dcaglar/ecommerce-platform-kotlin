package com.dogancaglar.ecommerceplatform.payment.api.dto

import java.math.BigDecimal

data class AmountDto(
    val value: BigDecimal,
    val currency: String
)