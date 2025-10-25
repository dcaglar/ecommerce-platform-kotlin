package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.Amount
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
            totalAmount = Amount(10000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(5000L, "USD")),
                PaymentLine(SellerId("seller-2"), Amount(5000L, "USD"))
            )
        )

        assertEquals(OrderId("order-123"), command.orderId)
        assertEquals(BuyerId("buyer-456"), command.buyerId)
        assertEquals(Amount(10000L, "USD"), command.totalAmount)
        assertEquals(2, command.paymentLines.size)
        assertEquals(SellerId("seller-1"), command.paymentLines[0].sellerId)
        assertEquals(Amount(5000L, "USD"), command.paymentLines[0].amount)
        assertEquals(SellerId("seller-2"), command.paymentLines[1].sellerId)
        assertEquals(Amount(5000L, "USD"), command.paymentLines[1].amount)
    }

    @Test
    fun `should create command with single payment line`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount(10000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(10000L, "USD"))
            )
        )

        assertEquals(1, command.paymentLines.size)
        assertEquals(Amount(10000L, "USD"), command.totalAmount)
    }

    @Test
    fun `should create command with zero amount`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount(0L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(0L, "USD"))
            )
        )

        assertEquals(Amount(0L, "USD"), command.totalAmount)
        assertEquals(Amount(0L, "USD"), command.paymentLines[0].amount)
    }

    @Test
    fun `should create command with different currencies`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount(10000L, "EUR"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(10000L, "EUR"))
            )
        )

        assertEquals(Amount(10000L, "EUR"), command.totalAmount)
        assertEquals("EUR", command.paymentLines[0].amount.currency)
    }

    @Test
    fun `should be immutable`() {
        val original = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount(10000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(10000L, "USD"))
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
            totalAmount = Amount(10000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(10000L, "USD"))
            )
        )

        val command2 = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount(10000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(10000L, "USD"))
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
            totalAmount = Amount(10000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(10000L, "USD"))
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
                totalAmount = Amount(10000L, "USD"),
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
                totalAmount = Amount(-1000L, "USD"),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount(1000L, "USD"))
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
                totalAmount = Amount(10000L, "USD"),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount(5000L, "USD")),
                    PaymentLine(SellerId("seller-2"), Amount(5000L, "EUR"))
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
                totalAmount = Amount(10000L, "USD"),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount(10000L, "EUR"))
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
                totalAmount = Amount(10000L, "USD"),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount(5000L, "USD")),
                    PaymentLine(SellerId("seller-2"), Amount(3000L, "USD"))
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
                totalAmount = Amount(5000L, "USD"),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount(5000L, "USD")),
                    PaymentLine(SellerId("seller-2"), Amount(3000L, "USD"))
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
                totalAmount = Amount(15000L, "USD"),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount(5000L, "USD")),
                    PaymentLine(SellerId("seller-2"), Amount(3000L, "USD"))
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
            totalAmount = Amount(largeAmount, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(largeAmount, "USD"))
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
            totalAmount = Amount(15000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(10000L, "USD")),
                PaymentLine(SellerId("seller-1"), Amount(5000L, "USD"))
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
                totalAmount = Amount(10000L, currency),
                paymentLines = listOf(
                    PaymentLine(SellerId("seller-1"), Amount(10000L, currency))
                )
            )

            assertEquals(currency, command.totalAmount.currency)
            assertEquals(currency, command.paymentLines[0].amount.currency)
        }
    }

    @Test
    fun `should handle fractional amounts correctly`() {
        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount(12345L, "USD"), // $123.45
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(12345L, "USD"))
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
            totalAmount = Amount(25000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(10000L, "USD")),
                PaymentLine(SellerId("seller-2"), Amount(5000L, "USD")),
                PaymentLine(SellerId("seller-3"), Amount(7500L, "USD")),
                PaymentLine(SellerId("seller-4"), Amount(2500L, "USD"))
            )
        )

        assertEquals(4, command.paymentLines.size)
        assertEquals(25000L, command.totalAmount.value)
        
        val sum = command.paymentLines.sumOf { it.amount.value }
        assertEquals(25000L, sum)
    }
}
