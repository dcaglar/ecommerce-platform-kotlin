package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter

import com.dogancaglar.paymentservice.domain.model.ledger.SettleStatus
import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentTxEntity

object PaymentTxEntityMapper {
     fun toEntity(domain: Tx): PaymentTxEntity = when (domain) {

        is Tx.AuthorizationTx -> PaymentTxEntity(
            txId              = domain.txId.value,
            txType            = domain.txType,
            paymentId         = domain.paymentId.value,
            paymentIntentId   = domain.paymentIntentId.value,
            parentTxId        = null,
            acquirerReference = domain.acquirerReference,
            amountValue       = domain.amount.quantity,
            amountCurrency    = domain.amount.currency.currencyCode,
            status            = domain.status.name,
            settleStatus      = null,
            acquirerBatchRef  = null,
            settledAmountValue = null,
            createdAt         = domain.createdAt
        )

        is Tx.CaptureTx -> PaymentTxEntity(
            txId              = domain.txId.value,
            txType            = domain.txType,
            paymentId         = domain.paymentId.value,
            paymentIntentId   = domain.paymentIntentId.value,
            parentTxId        = domain.authorizationTxId.value,
            acquirerReference = domain.acquirerReference,
            amountValue       = domain.amount.quantity,
            amountCurrency    = domain.amount.currency.currencyCode,
            status            = domain.status.name,
            settleStatus      = domain.settleStatus.name,
            acquirerBatchRef  = null,
            settledAmountValue = null,
            createdAt         = domain.createdAt
        )

        is Tx.RefundTx -> PaymentTxEntity(
            txId              = domain.txId.value,
            txType            = domain.txType,
            paymentId         = domain.paymentId.value,
            paymentIntentId   = domain.paymentIntentId.value,
            parentTxId        = domain.captureTxId.value,
            acquirerReference = domain.acquirerReference,
            amountValue       = domain.amount.quantity,
            amountCurrency    = domain.amount.currency.currencyCode,
            status            = domain.status.name,
            settleStatus      = null,
            acquirerBatchRef  = null,
            settledAmountValue = null,
            createdAt         = domain.createdAt
        )

        is Tx.SettleTx -> PaymentTxEntity(
            txId               = domain.txId.value,
            txType             = domain.txType,
            paymentId          = domain.paymentId.value,
            paymentIntentId    = domain.paymentIntentId.value,
            parentTxId         = domain.captureTxId.value,
            acquirerReference  = domain.acquirerBatchReference,
            amountValue        = domain.amount.quantity,
            amountCurrency     = domain.amount.currency.currencyCode,
            status             = domain.status.name,
            settleStatus       = if (domain.hasDiscrepancy) SettleStatus.DISCREPANCY.name
            else SettleStatus.MATCHED.name,
            acquirerBatchRef   = domain.acquirerBatchReference,
            settledAmountValue = domain.settledAmount.quantity,
            createdAt          = domain.createdAt
        )
    }
}