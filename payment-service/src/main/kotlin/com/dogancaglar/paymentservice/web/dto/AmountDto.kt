package com.dogancaglar.paymentservice.web.dto


import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class AmountDto(
    @field:NotNull
    @field:DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @field:Digits(integer = 10, fraction = 2, message = "Açmount must have up to 2 decimal places")
    val value: BigDecimal,

    @field:NotNull
    val currency: CurrencyEnum
)

enum class CurrencyEnum {
    EUR, USD, GBP
}