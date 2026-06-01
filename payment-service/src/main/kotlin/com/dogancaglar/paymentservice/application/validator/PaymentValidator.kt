package com.dogancaglar.paymentservice.application.validator

import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.AmountMapper
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.ProcessingModelDto
import org.springframework.stereotype.Service

@Service
class PaymentValidator {
    fun validate(request: CreatePaymentIntentRequestDTO) {
        validateProcessingModel(request)
        validateTotals(request)
        validateCurrencies(request)
    }

    private fun validateProcessingModel(request: CreatePaymentIntentRequestDTO) {
        val processingModel = request.processingModel
        require(
                (processingModel == ProcessingModelDto.DIRECT_MERCHANT && request.splits.isNullOrEmpty()) ||
                    (processingModel == ProcessingModelDto.MARKETPLACE && !request.splits.isNullOrEmpty())
        ) {
            "Invalid processing model: DIRECT_MERCHANT must have no splits, MARKETPLACE must have splits"
        }
    }

    private fun validateTotals(request: CreatePaymentIntentRequestDTO) {
        val processingModel = request.processingModel
        if (processingModel == ProcessingModelDto.MARKETPLACE) {
            val totalAmount = request.splits?.sumOf { it.amount.quantity }
            require(totalAmount == request.totalAmount.quantity) {
                "Sum of payment split amounts ($totalAmount) must exactly equal total amount (${request.totalAmount.quantity})"
            }
        }
    }

    private fun validateCurrencies(request: CreatePaymentIntentRequestDTO) {
        val paymentCurrency = AmountMapper.toDomain(request.totalAmount).currency

        request.splits?.forEach { split ->
            require(AmountMapper.toDomain(split.amount).currency == paymentCurrency) {
                "PaymentOrder for seller ${split.targetEntityId} has ${split.amount.currency}, " +
                        "but payment currency is $paymentCurrency"
            }
        }
    }
}