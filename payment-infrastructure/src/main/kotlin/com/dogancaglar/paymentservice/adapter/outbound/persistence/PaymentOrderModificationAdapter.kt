package com.dogancaglar.paymentservice.adapter.outbound.persistence

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentOrderEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentOrderMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Infrastructure adapter for PaymentOrderModificationPort.
 * Only handles persistence and domain entity mapping - no domain method calls.
 * Application services call domain methods first, then use this adapter to persist.
 * Similar pattern to PaymentOrderOutboundAdapter - Spring auto-wires this when PaymentOrderModificationPort is requested.
 */
@Repository
class PaymentOrderModificationAdapter(
    private val paymentOrderMapper: PaymentOrderMapper
) : PaymentOrderModificationPort {

    @Transactional(timeout = 2)
    override fun updateReturningIdempotent(order: PaymentOrder): PaymentOrder {
        // Assumes order is already modified by caller (domain method called in application service)
        val entity = PaymentOrderEntityMapper.toEntity(order)
        val updated = paymentOrderMapper.updateReturningIdempotent(entity)
        if (updated != null) return PaymentOrderEntityMapper.toDomain(updated)
        
        // No row updated → terminal or missing. Re-read to reflect truth if present.
        val found = paymentOrderMapper.findByPaymentOrderId(entity.paymentOrderId).firstOrNull()
        return found?.let(PaymentOrderEntityMapper::toDomain)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)
    }


    @Transactional(timeout = 2)
    override fun updateReturningIdempotentInitialCaptureRequest(paymentOrderId: Long): PaymentOrder {
        val updated = paymentOrderMapper.updateReturningIdempotentInitialCaptureRequest(
            paymentOrderId,
            Utc.nowLocalDateTime()
        )
        if (updated != null) return PaymentOrderEntityMapper.toDomain(updated)
        
        // No row updated → missing. Re-read to reflect truth if present.
        val found = paymentOrderMapper.findByPaymentOrderId(paymentOrderId).firstOrNull()
        return found?.let(PaymentOrderEntityMapper::toDomain)
            ?: throw MissingPaymentOrderException(paymentOrderId)
    }

    @Transactional(timeout = 2, readOnly = true)
    override fun findByPaymentOrderId(paymentOrderId: PaymentOrderId): PaymentOrder? {
        return paymentOrderMapper.findByPaymentOrderId(paymentOrderId.value)
            .map { PaymentOrderEntityMapper.toDomain(it) }
            .firstOrNull()
    }
}

class MissingPaymentOrderException(
    val paymentOrderId: Long,
    message: String = "PaymentOrder row is missing for id=$paymentOrderId"
) : RuntimeException(message)
