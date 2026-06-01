package com.dogancaglar.paymentservice.application.command

import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit
import com.dogancaglar.paymentservice.domain.model.payment.ProcessingModel
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId

data class CreatePaymentIntentCommand(
    val orderId: OrderId,
    val buyerId: BuyerId,
    val merchantAccountId: String,
    val processingModel: ProcessingModel,
    val totalAmount: Amount,
    val paymentSplits: List<PaymentSplit>
) {
    init {
        require(totalAmount.quantity > 0) { "Total amount must be positive" }
        if (processingModel == ProcessingModel.MARKETPLACE) {
            require(paymentSplits.isNotEmpty()) { "Payment splits cannot be empty for MARKETPLACE processing model" }
            require(hasConsistentCurrency()) { "All amounts must have same currency" }
            require(isValidTotalAmount()) { "Total amount must equal sum of payment splits" }
        } else {
            require(paymentSplits.isEmpty()) { "Payment splits must be empty for DIRECT_MERCHANT processing model" }
        }
    }

    private fun hasConsistentCurrency(): Boolean {
        val currencies = paymentSplits.map { it.amount.currency }.distinct()
        return currencies.size == 1 && currencies.first() == totalAmount.currency
    }

    private fun isValidTotalAmount(): Boolean {
        val sum = paymentSplits.sumOf { it.amount.quantity }
        return sum == totalAmount.quantity
    }
}
