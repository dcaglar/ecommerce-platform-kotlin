package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.common.db.entity.PaymentTxEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.PaymentTxMapper
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import org.springframework.stereotype.Repository
import com.dogancaglar.common.db.converter.PaymentTxEntityMapper

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
        mapper.findByPaymentId(paymentId).map { PaymentTxEntityMapper.toDomain(it) }
}
