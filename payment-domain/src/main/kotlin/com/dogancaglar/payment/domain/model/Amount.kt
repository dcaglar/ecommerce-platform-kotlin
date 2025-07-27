package com.dogancaglar.payment.domain.model

import java.math.BigDecimal

data class Amount(
    val value: BigDecimal,
    val currency: String
)