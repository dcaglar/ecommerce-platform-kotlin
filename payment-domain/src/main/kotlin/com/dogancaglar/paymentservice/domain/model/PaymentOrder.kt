package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import java.time.LocalDateTime

class PaymentOrder private constructor(
    val paymentOrderId: PaymentOrderId,
    val publicPaymentOrderId: String,
    val paymentId: PaymentId,
    val publicPaymentId: String,
    val sellerId: SellerId,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val retryCount: Int = 0,
    val retryReason: String? = null,
    val lastErrorMessage: String? = null
) {

    fun markAsFailed() = copy(status = PaymentOrderStatus.FAILED_TRANSIENT_ERROR)
    fun markAsPaid() = copy(status = PaymentOrderStatus.SUCCESSFUL_FINAL)
    fun markAsPending() = copy(status = PaymentOrderStatus.PENDING_STATUS_CHECK_LATER)
    fun markAsFinalizedFailed() = copy(status = PaymentOrderStatus.FAILED_FINAL)
    fun incrementRetry() = copy(retryCount = retryCount + 1)
    fun withRetryReason(reason: String?) = copy(retryReason = reason)
    fun withLastError(error: String?) = copy(lastErrorMessage = error)
    fun withUpdatedAt(now: LocalDateTime) = copy(updatedAt = now)

    fun isTerminal(): Boolean =
        status == PaymentOrderStatus.SUCCESSFUL_FINAL || status == PaymentOrderStatus.FAILED_FINAL

    private fun copy(
        status: PaymentOrderStatus = this.status,
        retryCount: Int = this.retryCount,
        retryReason: String? = this.retryReason,
        lastErrorMessage: String? = this.lastErrorMessage,
        updatedAt: LocalDateTime = this.updatedAt
    ) = PaymentOrder(
        paymentOrderId,
        publicPaymentOrderId,
        paymentId,
        publicPaymentId,
        sellerId,
        amount,
        status,
        createdAt,
        updatedAt,
        retryCount,
        retryReason,
        lastErrorMessage
    )

    companion object {
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var paymentOrderId: PaymentOrderId? = null
        private var publicPaymentOrderId: String? = null
        private var paymentId: PaymentId? = null
        private var publicPaymentId: String? = null
        private var sellerId: SellerId? = null
        private var amount: Amount? = null
        private var status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING
        private var createdAt: LocalDateTime = LocalDateTime.now()
        private var updatedAt: LocalDateTime = createdAt
        private var retryCount: Int = 0
        private var retryReason: String? = null
        private var lastErrorMessage: String? = null

        fun paymentOrderId(v: PaymentOrderId) = apply { paymentOrderId = v }
        fun publicPaymentOrderId(v: String) = apply { publicPaymentOrderId = v }
        fun paymentId(v: PaymentId) = apply { paymentId = v }
        fun publicPaymentId(v: String) = apply { publicPaymentId = v }
        fun sellerId(v: SellerId) = apply { sellerId = v }
        fun amount(v: Amount) = apply { amount = v }
        fun status(v: PaymentOrderStatus) = apply { status = v }
        fun createdAt(v: LocalDateTime) = apply { createdAt = v }
        fun updatedAt(v: LocalDateTime) = apply { updatedAt = v }
        fun retryCount(v: Int) = apply { retryCount = v }
        fun retryReason(v: String?) = apply { retryReason = v }
        fun lastErrorMessage(v: String?) = apply { lastErrorMessage = v }

        fun buildNew(): PaymentOrder = PaymentOrder(
            paymentOrderId = requireNotNull(paymentOrderId),
            publicPaymentOrderId = requireNotNull(publicPaymentOrderId),
            paymentId = requireNotNull(paymentId),
            publicPaymentId = requireNotNull(publicPaymentId),
            sellerId = requireNotNull(sellerId),
            amount = requireNotNull(amount),
            status = PaymentOrderStatus.INITIATED_PENDING,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

        fun buildFromPersistence(): PaymentOrder = PaymentOrder(
            paymentOrderId = requireNotNull(paymentOrderId),
            publicPaymentOrderId = requireNotNull(publicPaymentOrderId),
            paymentId = requireNotNull(paymentId),
            publicPaymentId = requireNotNull(publicPaymentId),
            sellerId = requireNotNull(sellerId),
            amount = requireNotNull(amount),
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            retryCount = retryCount,
            retryReason = retryReason,
            lastErrorMessage = lastErrorMessage
        )
    }
}