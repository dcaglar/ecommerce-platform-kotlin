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

@JvmInline
value class Currency(val currencyCode: String)

data class Amount private constructor(val quantity: Long,val currency: Currency){
    
    // Backward compatibility: alias for quantity
    val value: Long get() = quantity

    companion object {
        fun of(quantity: Long,currency: Currency): Amount{
            return Amount(quantity,currency)
        }
    }

    operator fun  plus(other: Amount): Amount{
        require(other.currency == currency){
            "currency not matching"
        }
        return Amount(quantity + other.quantity,currency)
    }

    operator fun  minus(other: Amount): Amount{
        require(other.currency == currency){
            "currency not matching"
        }
        return Amount(quantity-other.quantity,currency)
    }

    fun negate(): Amount = Amount(-quantity,currency)

    fun isPositive(): Boolean = quantity > 0

    fun isNegative(): Boolean = quantity<0

}