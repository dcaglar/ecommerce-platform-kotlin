package com.dogancaglar.paymentservice.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AmountTest {

    @Test
    fun `should create Amount with value and currency`() {
        val amount = Amount(100000L, "USD") // $1000.00 = 100000 cents

        assertEquals(100000L, amount.value)
        assertEquals("USD", amount.currency)
    }

    @Test
    fun `should create Amount with zero value`() {
        val amount = Amount(0L, "USD")

        assertEquals(0L, amount.value)
        assertEquals("USD", amount.currency)
    }

    @Test
    fun `should create Amount with decimal equivalent value`() {
        val amount = Amount(9999L, "EUR") // €99.99 = 9999 cents

        assertEquals(9999L, amount.value)
        assertEquals("EUR", amount.currency)
    }

    @Test
    fun `should support different currencies`() {
        val usd = Amount(10000L, "USD") // $100.00
        val eur = Amount(10000L, "EUR") // €100.00
        val gbp = Amount(10000L, "GBP") // £100.00
        val jpy = Amount(10000L, "JPY") // ¥100.00

        assertEquals("USD", usd.currency)
        assertEquals("EUR", eur.currency)
        assertEquals("GBP", gbp.currency)
        assertEquals("JPY", jpy.currency)
    }

    @Test
    fun `equals should return true for same values`() {
        val amount1 = Amount(100000L, "USD")
        val amount2 = Amount(100000L, "USD")

        assertEquals(amount1, amount2)
    }

    @Test
    fun `equals should return false for different values`() {
        val amount1 = Amount(100000L, "USD")
        val amount2 = Amount(200000L, "USD")

        assertNotEquals(amount1, amount2)
    }

    @Test
    fun `equals should return false for different currencies`() {
        val amount1 = Amount(100000L, "USD")
        val amount2 = Amount(100000L, "EUR")

        assertNotEquals(amount1, amount2)
    }

    @Test
    fun `hashCode should be equal for equal amounts`() {
        val amount1 = Amount(100000L, "USD")
        val amount2 = Amount(100000L, "USD")

        assertEquals(amount1.hashCode(), amount2.hashCode())
    }

    @Test
    fun `copy should create new instance with modified value`() {
        val original = Amount(100000L, "USD")
        val copied = original.copy(value = 200000L)

        assertEquals(100000L, original.value)
        assertEquals(200000L, copied.value)
        assertEquals("USD", copied.currency)
    }

    @Test
    fun `copy should create new instance with modified currency`() {
        val original = Amount(100000L, "USD")
        val copied = original.copy(currency = "EUR")

        assertEquals("USD", original.currency)
        assertEquals("EUR", copied.currency)
        assertEquals(100000L, copied.value)
    }

    @Test
    fun `should handle large values`() {
        val largeValue = 999999999999L // $9,999,999,999.99
        val amount = Amount(largeValue, "USD")

        assertEquals(largeValue, amount.value)
    }

    @Test
    fun `should handle negative values`() {
        val negativeValue = -10000L // -$100.00
        val amount = Amount(negativeValue, "USD")

        assertEquals(negativeValue, amount.value)
    }

    @Test
    fun `should preserve Long precision`() {
        val preciseValue = 123456789L // $1,234,567.89
        val amount = Amount(preciseValue, "USD")

        assertEquals(preciseValue, amount.value)
    }

    @Test
    fun `should handle cent values correctly`() {
        val oneCent = Amount(1L, "USD") // $0.01
        val ninetNineCents = Amount(99L, "USD") // $0.99
        val oneDollar = Amount(100L, "USD") // $1.00

        assertEquals(1L, oneCent.value)
        assertEquals(99L, ninetNineCents.value)
        assertEquals(100L, oneDollar.value)
    }

    @Test
    fun `toString should include value and currency`() {
        val amount = Amount(100000L, "USD")
        val string = amount.toString()

        assertTrue(string.contains("100000"))
        assertTrue(string.contains("USD"))
    }

    @Test
    fun `should be usable in collections`() {
        val amounts = listOf(
            Amount(10000L, "USD"), // $100.00
            Amount(20000L, "EUR"), // €200.00
            Amount(30000L, "GBP")  // £300.00
        )

        assertEquals(3, amounts.size)
        assertTrue(amounts.contains(Amount(10000L, "USD")))
    }

    @Test
    fun `should be usable as map key`() {
        val amountMap = mapOf(
            Amount(10000L, "USD") to "One hundred dollars",
            Amount(20000L, "EUR") to "Two hundred euros"
        )

        assertEquals("One hundred dollars", amountMap[Amount(10000L, "USD")])
        assertEquals("Two hundred euros", amountMap[Amount(20000L, "EUR")])
    }

    @Test
    fun `should handle very small values`() {
        val smallValue = 1L // $0.01
        val amount = Amount(smallValue, "USD")

        assertEquals(smallValue, amount.value)
    }

    @Test
    fun `should create independent instances`() {
        val value = 100000L
        val amount1 = Amount(value, "USD")
        val amount2 = Amount(value, "USD")

        assertEquals(amount1, amount2)
        assertTrue(amount1 !== amount2) // Different objects
    }

    @Test
    fun `should handle common dollar amounts correctly`() {
        val cases = mapOf(
            99L to "99 cents",
            100L to "1 dollar",
            2000L to "20 dollars",
            150L to "1.50 dollars",
            999999L to "9999.99 dollars"
        )

        cases.forEach { (cents, description) ->
            val amount = Amount(cents, "USD")
            assertEquals(cents, amount.value, "Failed for $description")
        }
    }
}
