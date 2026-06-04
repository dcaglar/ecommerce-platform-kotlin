package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.common.db.entity.PaymentTxEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/**
 * PaymentTxMapper
 *
 * MyBatis mapper interface for the central DB 'payment_tx' table.
 * All SQL is defined in:
 *   payment-consumers/src/main/resources/mapper/PaymentTxMapper.xml
 */
@Mapper
interface PaymentTxMapper {

    fun insert(entity: PaymentTxEntity)
    
    fun upsert(entity: PaymentTxEntity)

    fun findByPaymentId(paymentId: Long): List<PaymentTxEntity>

    /**
     * Updates settle_status, settled_amount_value, and acquirer_batch_ref
     * for a CAPTURE row after settlement batch reconciliation.
     */
    fun updateSettleStatus(
        @Param("txId")                txId: Long,
        @Param("settleStatus")        settleStatus: String,
        @Param("settledAmountValue")  settledAmountValue: Long,
        @Param("acquirerBatchRef")    acquirerBatchRef: String
    )

    /**
     * Updates the status of a tx row (e.g., PENDING → SUCCESS or FAILED)
     * after receiving a PSP webhook confirmation.
     */
    fun updateStatus(
        @Param("txId")   txId: Long,
        @Param("status") status: String
    )
}

