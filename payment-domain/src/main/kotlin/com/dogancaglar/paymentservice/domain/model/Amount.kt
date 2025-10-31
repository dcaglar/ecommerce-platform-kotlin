package com.dogancaglar.paymentservice.domain.model

/**
 * Represents a monetary amount using the smallest currency unit (e.g., cents, pence).
 *
 * Examples:
 * - $0.99 USD = Amount(quantity = 99, currency = Currency("USD"))
 * - $20.00 USD = Amount(quantity = 2000, currency = Currency("USD"))
 * - €15.50 EUR = Amount(quantity = 1550, currency = Currency("EUR"))
 *
 * This approach avoids floating-point precision issues and rounding errors.
 * 
 * Note: Amounts must be created using the factory method Amount.of() which enforces
 * that quantity > 0. This prevents zero or negative amounts from being created.
 */

@JvmInline
value class Currency(val currencyCode: String)

data class Amount private constructor(val quantity: Long,val currency: Currency){
    

    companion object {
        fun of(quantity: Long, currency: Currency): Amount {
            require(quantity > 0) { "Amount quantity must be greater than zero, but was: $quantity" }
            return Amount(quantity, currency)
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