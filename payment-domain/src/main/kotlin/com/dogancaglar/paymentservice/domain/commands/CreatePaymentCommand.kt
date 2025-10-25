package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine

data class CreatePaymentCommand(
    val orderId: OrderId,
    val buyerId: BuyerId,
    val totalAmount: Amount,
    val paymentLines: List<PaymentLine>        // sellerId + amount each
) {
    init {
        require(paymentLines.isNotEmpty()) { "Payment lines cannot be empty" }
        require(totalAmount.value >= 0) { "Total amount cannot be negative" }
        require(hasConsistentCurrency()) { "All amounts must have same currency" }
        require(isValidTotalAmount()) { "Total amount must equal sum of payment lines" }
    }
    
    private fun hasConsistentCurrency(): Boolean {
        val currencies = paymentLines.map { it.amount.currency }.distinct()
        return currencies.size == 1 && currencies.first() == totalAmount.currency
    }
    
    private fun isValidTotalAmount(): Boolean {
        val sum = paymentLines.sumOf { it.amount.value }
        return sum == totalAmount.value
    }
}