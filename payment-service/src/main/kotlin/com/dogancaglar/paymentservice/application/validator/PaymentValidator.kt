package com.dogancaglar.paymentservice.application.validator

import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.AmountMapper
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import org.springframework.stereotype.Service

@Service
class PaymentValidator {
    fun validate(request: CreatePaymentIntentRequestDTO) {
        validateUniqueSellers(request)
        validateTotals(request)
        validateCurrencies(request)
    }

    private fun validateUniqueSellers(request: CreatePaymentIntentRequestDTO) {
        val sellerIds = request.paymentOrders.map { it.sellerId }
        require(sellerIds.distinct().size == sellerIds.size) {
            "Duplicate seller IDs are not allowed in a single payment"
        }
    }

    private fun validateTotals(request: CreatePaymentIntentRequestDTO) {
        val totalOrders = request.paymentOrders.sumOf { it.amount.quantity }
        require(totalOrders == request.totalAmount.quantity) {
            "Sum of payment order amounts ($totalOrders) must equal total amount (${request.totalAmount.quantity})"
        }
    }

    private fun validateCurrencies(request: CreatePaymentIntentRequestDTO) {
        val paymentCurrency = AmountMapper.toDomain(request.totalAmount).currency

        request.paymentOrders.forEach { order ->
            require(AmountMapper.toDomain(order.amount).currency == paymentCurrency) {
                "PaymentOrder for seller ${order.sellerId} has ${order.amount.currency}, " +
                        "but payment currency is $paymentCurrency"
            }
        }
    }
}