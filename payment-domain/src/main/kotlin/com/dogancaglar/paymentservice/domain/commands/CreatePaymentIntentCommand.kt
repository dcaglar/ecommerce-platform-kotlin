package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine

data class CreatePaymentIntentCommand(
    val orderId: OrderId,
    val buyerId: BuyerId,
    val totalAmount: Amount,
    val paymentOrderLines: List<PaymentOrderLine>        // sellerId + amount each
) {
    init {
        require(paymentOrderLines.isNotEmpty()) { "Payment lines cannot be empty" }
        require(totalAmount.quantity >= 0) { "Total amount cannot be negative" }
        require(hasConsistentCurrency()) { "All amounts must have same currency" }
        require(isValidTotalAmount()) { "Total amount must equal sum of payment lines" }
    }
    
    private fun hasConsistentCurrency(): Boolean {
        val currencies = paymentOrderLines.map { it.amount.currency }.distinct()
        return currencies.size == 1 && currencies.first() == totalAmount.currency
    }
    
    private fun isValidTotalAmount(): Boolean {
        val sum = paymentOrderLines.sumOf { it.amount.quantity }
        return sum == totalAmount.quantity
    }
}