package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentEntity
import org.apache.ibatis.annotations.Mapper

/**
 * PaymentMapper
 *
 * MyBatis mapper interface for the central DB 'payments' table.
 * The Payment aggregate is owned by the Central Core Cluster (payment-consumers),
 * not the Edge Cell (payment-service).
 *
 * All SQL is defined in:
 *   payment-consumers/src/main/resources/mapper/PaymentMapper.xml
 */
@Mapper
interface PaymentMapper {

    fun getMaxPaymentId(): Long?

    fun insert(payment: PaymentEntity): Int

    fun findById(id: Long): PaymentEntity?

    fun findByPaymentIntentId(paymentIntentId: Long): PaymentEntity?

    /**
     * Updates only mutable state: capturedAmountValue, refundedAmountValue,
     * status, updatedAt. Immutable fields (paymentId, merchantAccountId,
     * processingModel, splitsJson, etc.) are never mutated after INSERT.
     */
    fun update(payment: PaymentEntity): Int

    fun upsert(payment: PaymentEntity): Int

    fun deleteById(id: Long): Int

    fun deleteAll(): Int
}
