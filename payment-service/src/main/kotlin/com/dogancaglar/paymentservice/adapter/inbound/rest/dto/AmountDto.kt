package com.dogancaglar.port.out.web.dto


import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

/**
 * Amount DTO using smallest currency unit (cents, pence, etc.)
 *
 * Examples:
 * - $0.99 USD = AmountDto(value = 99, currency = USD)
 * - $20.00 USD = AmountDto(value = 2000, currency = USD)
 * - €15.50 EUR = AmountDto(value = 1550, currency = EUR)
 */
data class AmountDto(
    @field:NotNull
    @field:Min(value = 1, message = "Amount must be greater than zero")
    val value: Long,

    @field:NotNull
    val currency: CurrencyEnum
)

enum class CurrencyEnum {
    EUR, USD, GBP
}