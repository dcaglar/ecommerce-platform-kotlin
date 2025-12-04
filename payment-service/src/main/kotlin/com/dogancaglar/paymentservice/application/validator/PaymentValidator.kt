package com.dogancaglar.paymentservice.application.validator

import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.AmountMapper
import com.dogancaglar.paymentservice.domain.model.ledger.AccountStatus
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentRequestDTO
import org.springframework.stereotype.Service

@Service
class PaymentValidator(private val accountDirectory: AccountDirectoryPort) {
    fun validate(request: CreatePaymentRequestDTO) {
        validateUniqueSellers(request)
        validateTotals(request)
        validateCurrencies(request)
    }

    private fun validateUniqueSellers(request: CreatePaymentRequestDTO) {
        val sellerIds = request.paymentOrders.map { it.sellerId }
        require(sellerIds.distinct().size == sellerIds.size) {
            "Duplicate seller IDs are not allowed in a single payment"
        }
    }

    private fun validateTotals(request: CreatePaymentRequestDTO) {
        val totalOrders = request.paymentOrders.sumOf { it.amount.quantity }
        require(totalOrders == request.totalAmount.quantity) {
            "Sum of payment order amounts ($totalOrders) must equal total amount (${request.totalAmount.quantity})"
        }
    }
    private fun validateCurrencies(request: CreatePaymentRequestDTO) {
        val paymentCurrency = AmountMapper.toDomain(request.totalAmount).currency

        request.paymentOrders.forEach { order ->
            require(AmountMapper.toDomain(order.amount).currency == paymentCurrency) {
                "PaymentOrder for seller ${order.sellerId} has ${order.amount.currency}, " +
                        "but payment currency is $paymentCurrency"
            }

            val profile = accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, order.sellerId)
            require(profile.currency == paymentCurrency) {
                "Seller ${order.sellerId} settles in ${profile.currency}, " +
                        "but payment is in $paymentCurrency"
            }

            require(profile.status == AccountStatus.ACTIVE) {
                "Seller ${order.sellerId} is not active"
            }
        }
    }}