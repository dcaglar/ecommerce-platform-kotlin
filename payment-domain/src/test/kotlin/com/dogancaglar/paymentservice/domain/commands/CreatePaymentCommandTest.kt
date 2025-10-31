package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CreatePaymentCommandTest {

    @Test
    fun `should create command with valid data`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(5000L, Currency("USD"))),
                PaymentLine(SellerId("seller-2"), Amount.of(5000L, Currency("USD")))
            )
        )

        assertEquals(OrderId("order-123"), command.orderId)
        assertEquals(BuyerId("buyer-456"), command.buyerId)
        assertEquals(Amount.of(10000L, Currency("USD")), command.totalAmount)
        assertEquals(2, command.paymentLines.size)
        assertEquals(SellerId("seller-1"), command.paymentLines[0].sellerId)
        assertEquals(Amount.of(5000L, Currency("USD")), command.paymentLines[0].amount)
        assertEquals(SellerId("seller-2"), command.paymentLines[1].sellerId)
        assertEquals(Amount.of(5000L, Currency("USD")), command.paymentLines[1].amount)
    }

    @Test
    fun `should create command with single payment line`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency("USD")))
            )
        )

        assertEquals(1, command.paymentLines.size)
        assertEquals(Amount.of(10000L, Currency("USD")), command.totalAmount)
    }

    @Test
    fun `should create command with zero amount`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(0L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(0L, Currency("USD")))
            )
        )

        assertEquals(Amount.of(0L, Currency("USD")), command.totalAmount)
        assertEquals(Amount.of(0L, Currency("USD")), command.paymentLines[0].amount)
    }

    @Test
    fun `should create command with different currencies`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(10000L, Currency("EUR")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency("EUR")))
            )
        )

        assertEquals(Amount.of(10000L, Currency("EUR")), command.totalAmount)
        assertEquals("EUR", command.paymentLines[0].amount.currency.currencyCode)
    }

    @Test
    fun `should be immutable`() {
        val original = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency("USD")))
            )
        )

        val modified = original.copy(orderId = OrderId("new-order"))

        assertNotEquals(original.orderId, modified.orderId)
        assertEquals(original.buyerId, modified.buyerId)
        assertEquals(original.totalAmount, modified.totalAmount)
        assertEquals(original.paymentLines, modified.paymentLines)
    }

    @Test
    fun `should support equality`() {
        val command1 = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency("USD")))
            )
        )

        val command2 = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency("USD")))
            )
        )

        assertEquals(command1, command2)
        assertEquals(command1.hashCode(), command2.hashCode())
    }

    @Test
    fun `should support copy with modifications`() {
        val original = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency("USD")))
            )
        )

        val modified = original.copy(
            orderId = OrderId("new-order"),
            buyerId = BuyerId("new-buyer")
        )

        assertEquals(OrderId("new-order"), modified.orderId)
        assertEquals(BuyerId("new-buyer"), modified.buyerId)
        assertEquals(original.totalAmount, modified.totalAmount)
        assertEquals(original.paymentLines, modified.paymentLines)
    }

    // Validation Tests

    @Test
    fun `should throw exception when payment lines are empty`() {
        val exception = assertThrows<IllegalArgumentException> {
            CreatePaymentCommand(
                orderId = OrderId("order-123"),
                buyerId = BuyerId("buyer-456"),
                totalAmount = Amount.of(10000L, Currency("USD")),
                paymentLines = emptyList()
            )
        }

        assertEquals("Payment lines cannot be empty", exception.message)
    }

    @Test
    fun `should throw exception when total amount is negative`() {
        val exception = assertThrows<IllegalArgumentException> {
            CreatePaymentCommand(
                orderId = OrderId("order-123"),
                buyerId = BuyerId("buyer-456"),
                totalAmount = Amount.of(-1000L, Currency("USD")),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount.of(1000L, Currency("USD")))
                )
            )
        }

        assertEquals("Total amount cannot be negative", exception.message)
    }

    @Test
    fun `should throw exception when currencies are inconsistent`() {
        val exception = assertThrows<IllegalArgumentException> {
            CreatePaymentCommand(
                orderId = OrderId("order-123"),
                buyerId = BuyerId("buyer-456"),
                totalAmount = Amount.of(10000L, Currency("USD")),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount.of(5000L, Currency("USD"))),
                    PaymentLine(SellerId("seller-2"), Amount.of(5000L, Currency("EUR")))
                )
            )
        }

        assertEquals("All amounts must have same currency", exception.message)
    }

    @Test
    fun `should throw exception when total amount currency differs from payment lines`() {
        val exception = assertThrows<IllegalArgumentException> {
            CreatePaymentCommand(
                orderId = OrderId("order-123"),
                buyerId = BuyerId("buyer-456"),
                totalAmount = Amount.of(10000L, Currency("USD")),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency("EUR")))
                )
            )
        }

        assertEquals("All amounts must have same currency", exception.message)
    }

    @Test
    fun `should throw exception when total amount does not equal sum of payment lines`() {
        val exception = assertThrows<IllegalArgumentException> {
            CreatePaymentCommand(
                orderId = OrderId("order-123"),
                buyerId = BuyerId("buyer-456"),
                totalAmount = Amount.of(10000L, Currency("USD")),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount.of(5000L, Currency("USD"))),
                    PaymentLine(SellerId("seller-2"), Amount.of(3000L, Currency("USD")))
                )
            )
        }

        assertEquals("Total amount must equal sum of payment lines", exception.message)
    }

    @Test
    fun `should throw exception when total amount is less than sum of payment lines`() {
        val exception = assertThrows<IllegalArgumentException> {
            CreatePaymentCommand(
                orderId = OrderId("order-123"),
                buyerId = BuyerId("buyer-456"),
                totalAmount = Amount.of(5000L, Currency("USD")),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount.of(5000L, Currency("USD"))),
                    PaymentLine(SellerId("seller-2"), Amount.of(3000L, Currency("USD")))
                )
            )
        }

        assertEquals("Total amount must equal sum of payment lines", exception.message)
    }

    @Test
    fun `should throw exception when total amount is greater than sum of payment lines`() {
        val exception = assertThrows<IllegalArgumentException> {
            CreatePaymentCommand(
                orderId = OrderId("order-123"),
                buyerId = BuyerId("buyer-456"),
                totalAmount = Amount.of(15000L, Currency("USD")),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount.of(5000L, Currency("USD"))),
                    PaymentLine(SellerId("seller-2"), Amount.of(3000L, Currency("USD")))
                )
            )
        }

        assertEquals("Total amount must equal sum of payment lines", exception.message)
    }

    // Edge Cases

    @Test
    fun `should handle large amounts correctly`() {
        val largeAmount = 999999999L
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(largeAmount, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(largeAmount, Currency("USD")))
            )
        )

        assertEquals(largeAmount, command.totalAmount.value)
        assertEquals(largeAmount, command.paymentLines[0].amount.value)
    }

    @Test
    fun `should handle multiple payment lines with same seller`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(15000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency("USD"))),
                PaymentLine(SellerId("seller-1"), Amount.of(5000L, Currency("USD")))
            )
        )

        assertEquals(2, command.paymentLines.size)
        assertEquals(SellerId("seller-1"), command.paymentLines[0].sellerId)
        assertEquals(SellerId("seller-1"), command.paymentLines[1].sellerId)
    }

    @Test
    fun `should handle different currencies correctly`() {
        val currencies = listOf("USD", "EUR", "GBP", "JPY", "TRY")

        currencies.forEach { currency ->
            val command = CreatePaymentCommand(
                orderId = OrderId("order-123"),
                buyerId = BuyerId("buyer-456"),
                totalAmount = Amount.of(10000L, Currency(currency)),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency(currency)))
                )
            )

            assertEquals(currency, command.totalAmount.currency.currencyCode)
            assertEquals(currency, command.paymentLines[0].amount.currency.currencyCode)
        }
    }

    @Test
    fun `should handle fractional amounts correctly`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(12345L, Currency("USD")), // $123.45
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(12345L, Currency("USD")))
            )
        )

        assertEquals(12345L, command.totalAmount.value)
        assertEquals(12345L, command.paymentLines[0].amount.value)
    }

    @Test
    fun `should handle complex payment line scenarios`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount.of(25000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount.of(10000L, Currency("USD"))),
                PaymentLine(SellerId("seller-2"), Amount.of(5000L, Currency("USD"))),
                PaymentLine(SellerId("seller-3"), Amount.of(7500L, Currency("USD"))),
                PaymentLine(SellerId("seller-4"), Amount.of(2500L, Currency("USD")))
            )
        )

        assertEquals(4, command.paymentLines.size)
        assertEquals(25000L, command.totalAmount.value)
        
        val sum = command.paymentLines.sumOf { it.amount.value }
        assertEquals(25000L, sum)
    }
}
