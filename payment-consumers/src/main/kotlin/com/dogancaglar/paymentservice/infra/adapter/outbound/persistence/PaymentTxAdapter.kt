package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.ledger.PaymentTx
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentTxEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.PaymentTxMapper
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class PaymentTxAdapter(
    private val mapper: PaymentTxMapper
) : PaymentTxPort {

    override fun save(paymentTx: PaymentTx) {
        val entity = PaymentTxEntity(
            txId = paymentTx.txId,
            txType = paymentTx.txType,
            parentTxId = paymentTx.parentTxId,
            paymentId = paymentTx.paymentId,
            paymentOrderId = paymentTx.paymentOrderId,
            acquirerReference = paymentTx.acquirerReference,
            amountValue = paymentTx.amount.quantity,
            amountCurrency = paymentTx.amount.currency.currencyCode,
            createdAt = paymentTx.createdAt
        )
        mapper.insert(entity)
    }

    override fun findByPaymentId(paymentId: Long): List<PaymentTx> {
        val entities = mapper.findByPaymentId(paymentId)
        return entities.map { toDomain(it) }
    }

    private fun toDomain(entity: PaymentTxEntity): PaymentTx {
        // Constructing domain amount
        val amount = com.dogancaglar.paymentservice.domain.model.common.Amount.of(
            quantity = entity.amountValue,
            currency = com.dogancaglar.paymentservice.domain.model.common.Currency(entity.amountCurrency)
        )
        val createdAt = entity.createdAt ?: Instant.now()

        return when (entity.txType) {
            "AUTHORIZATION" -> PaymentTx.Authorization(
                txId = entity.txId,
                paymentId = entity.paymentId,
                acquirerReference = entity.acquirerReference,
                amount = amount,
                createdAt = createdAt
            )
            "CAPTURE" -> PaymentTx.Capture(
                txId = entity.txId,
                paymentId = entity.paymentId,
                paymentOrderId = entity.paymentOrderId!!,
                authorizationTxId = entity.parentTxId!!,
                acquirerReference = entity.acquirerReference,
                amount = amount,
                createdAt = createdAt
            )
            "REFUND" -> PaymentTx.Refund(
                txId = entity.txId,
                paymentId = entity.paymentId,
                paymentOrderId = entity.paymentOrderId!!,
                captureTxId = entity.parentTxId!!,
                acquirerReference = entity.acquirerReference,
                amount = amount,
                createdAt = createdAt
            )
            else -> throw IllegalStateException("Unknown transaction type: ${entity.txType}")
        }
    }
}
