package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.domain.model.ledger.SettleStatus
import com.dogancaglar.paymentservice.domain.model.ledger.TxStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentTxEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.PaymentTxMapper
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import org.springframework.stereotype.Repository
import java.time.Instant
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter.PaymentTxEntityMapper

/**
 * PaymentTxAdapter
 *
 * Outbound persistence adapter implementing [PaymentTxPort].
 * Translates between the [Tx] sealed domain hierarchy and the
 * flat [PaymentTxEntity] DB POJO.
 *
 * Strict separation rules enforced here:
 *  - The domain [Tx] subclasses use typed VOs (PaymentId, TxId, TxStatus, SettleStatus).
 *  - The [PaymentTxEntity] uses raw primitives (Long, String).
 *  - This adapter is the ONLY crossing point. No domain type leaks into the DB POJO,
 *    and no infrastructure type leaks into the domain sealed class.
 *
 * Subclass dispatch for [save]:
 *  - AuthorizationTx → parentTxId = null, status = SUCCESS (auth is synchronous), settleStatus = null
 *  - CaptureTx       → parentTxId = authorizationTxId, status from domain, settleStatus = UNMATCHED
 *  - RefundTx        → parentTxId = captureTxId, status from domain, settleStatus = null
 *  - SettleTx        → parentTxId = captureTxId, status = SUCCESS, settleStatus from domain
 *
 * Subclass dispatch for [toDomain]:
 *  - Switch on txType string, reconstruct the correct sealed subclass.
 *  - SETTLE rows carry acquirerBatchRef and settledAmountValue which are null for other types.
 */
@Repository
class PaymentTxAdapter(
    private val mapper: PaymentTxMapper
) : PaymentTxPort {

    override fun save(tx: Tx) {
        val entity = PaymentTxEntityMapper.toEntity(tx)
        mapper.insert(entity)
    }

    override fun findByPaymentId(paymentId: Long): List<Tx> =
        mapper.findByPaymentId(paymentId).map { toDomain(it) }

    // =========================================================================
    // Domain → Entity (downgrade to raw primitives for DB storage)
    // =========================================================================


    // =========================================================================
    // Entity → Domain (lift raw primitives back to typed domain VOs)
    // =========================================================================

    private fun toDomain(entity: PaymentTxEntity): Tx {
        val amount    = Amount.of(entity.amountValue, Currency(entity.amountCurrency))
        val createdAt = entity.createdAt ?: Instant.now()
        val status    = TxStatus.valueOf(entity.status)
        val paymentIntentId = PaymentIntentId(entity.paymentIntentId)

        return when (entity.txType) {

            "AUTHORIZATION" -> Tx.AuthorizationTx(
                txId              = TxId(entity.txId),
                paymentId         = PaymentId(entity.paymentId),
                paymentIntentId   = paymentIntentId,
                acquirerReference = entity.acquirerReference,
                amount            = amount,
                status            = status,
                createdAt         = createdAt
            )

            "CAPTURE" -> Tx.CaptureTx(
                txId              = TxId(entity.txId),
                paymentId         = PaymentId(entity.paymentId),
                paymentIntentId   = paymentIntentId,
                authorizationTxId = TxId(requireNotNull(entity.parentTxId) {
                    "CAPTURE row txId=${entity.txId} is missing parentTxId (authorizationTxId)"
                }),
                acquirerReference = entity.acquirerReference,
                amount            = amount,
                status            = status,
                settleStatus      = entity.settleStatus
                    ?.let { SettleStatus.valueOf(it) }
                    ?: SettleStatus.UNMATCHED,
                createdAt         = createdAt
            )

            "REFUND" -> Tx.RefundTx(
                txId              = TxId(entity.txId),
                paymentId         = PaymentId(entity.paymentId),
                paymentIntentId   = paymentIntentId,
                captureTxId       = TxId(requireNotNull(entity.parentTxId) {
                    "REFUND row txId=${entity.txId} is missing parentTxId (captureTxId)"
                }),
                acquirerReference = entity.acquirerReference,
                amount            = amount,
                status            = status,
                createdAt         = createdAt
            )

            "SETTLE" -> Tx.SettleTx(
                txId                   = TxId(entity.txId),
                paymentId              = PaymentId(entity.paymentId),
                paymentIntentId        = paymentIntentId,
                captureTxId            = TxId(requireNotNull(entity.parentTxId) {
                    "SETTLE row txId=${entity.txId} is missing parentTxId (captureTxId)"
                }),
                acquirerBatchReference = requireNotNull(entity.acquirerBatchRef) {
                    "SETTLE row txId=${entity.txId} is missing acquirerBatchRef"
                },
                settledAmount          = Amount.of(
                    requireNotNull(entity.settledAmountValue) {
                        "SETTLE row txId=${entity.txId} is missing settledAmountValue"
                    },
                    Currency(entity.amountCurrency)
                ),
                amount                 = amount,
                status                 = status,
                createdAt              = createdAt
            )

            else -> throw IllegalStateException(
                "Unknown Tx type '${entity.txType}' for txId=${entity.txId}. " +
                "Expected one of: AUTHORIZATION, CAPTURE, REFUND, SETTLE"
            )
        }
    }
}
