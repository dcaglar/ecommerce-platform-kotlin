package com.dogancaglar.paymentservice.domain.model

/**
 * Represents a monetary amount using the smallest currency unit (e.g., cents, pence).
 *
 * Examples:
 * - $0.99 USD = Amount(value = 99, currency = "USD")
 * - $20.00 USD = Amount(value = 2000, currency = "USD")
 * - €15.50 EUR = Amount(value = 1550, currency = "EUR")
 *
 * This approach avoids floating-point precision issues and rounding errors.
 */
data class Amount(
    val value: Long,
    val currency: String
)