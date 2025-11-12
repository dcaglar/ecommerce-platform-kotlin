package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import java.time.Clock
import java.time.LocalDateTime


class PaymentOrder private constructor(
    val paymentOrderId: PaymentOrderId,
    val paymentId: PaymentId,
    val sellerId: SellerId,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val retryCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {

    val publicPaymentOrderId: String
        get() = "paymentorder-${paymentOrderId.value}"
    val publicPaymentId: String
        get() = "payment-${paymentId.value}"
    fun incrementRetry() = copy(retryCount = retryCount + 1)
    fun markCaptureRequested(): PaymentOrder {
        require(status == PaymentOrderStatus.INITIATED_PENDING)
        {
            "Invalid transtion from ${status.name} to ${PaymentOrderStatus.CAPTURE_REQUESTED}"
        }
        return copy(status = PaymentOrderStatus.CAPTURE_REQUESTED)
    }

    fun withUpdateAt(updatedAt: LocalDateTime): PaymentOrder {
        return copy(updatedAt = updatedAt)
    }

    fun markAsCaptured(): PaymentOrder {
        require(status == PaymentOrderStatus.CAPTURE_REQUESTED)
        {
            "Invalid transtion from ${status.name} to ${PaymentOrderStatus.CAPTURED}"
        }
        return copy(status = PaymentOrderStatus.CAPTURED)
    }

    fun isTerminal(): Boolean =
        status == PaymentOrderStatus.CAPTURED || status == PaymentOrderStatus.CAPTURE_FAILED


    fun markCaptureDeclined(): PaymentOrder {
        require(
            status in listOf(
                PaymentOrderStatus.CAPTURE_REQUESTED
            )
        ) {
            "Invalid transtion from ${status.name} to ${PaymentOrderStatus.CAPTURE_FAILED}"
        }
        return copy(status = PaymentOrderStatus.CAPTURE_FAILED)
    }

    private fun copy(
        status: PaymentOrderStatus = this.status,
        retryCount: Int = this.retryCount,
        updatedAt: LocalDateTime = LocalDateTime.now()
    ): PaymentOrder = PaymentOrder(
        paymentOrderId = paymentOrderId,
        paymentId = paymentId,
        sellerId = sellerId,
        amount = amount,
        status = status,
        retryCount = retryCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun createNew(
            paymentOrderId: PaymentOrderId,
            paymentId: PaymentId,
            sellerId: SellerId,
            amount: Amount
        ): PaymentOrder {
            require(amount.isPositive()) { "Total amount must be positive " }
            require(sellerId.value.isNotBlank()) { "Seller id cant be blank" }
            return PaymentOrder(
                paymentOrderId = paymentOrderId,
                paymentId = paymentId,
                sellerId = sellerId,
                amount = amount,
                status = PaymentOrderStatus.INITIATED_PENDING,
                retryCount = 0,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        }





        fun rehydrate(
            paymentOrderId: PaymentOrderId,
            paymentId: PaymentId,
            sellerId: SellerId,
            amount: Amount,
            status: PaymentOrderStatus,
            retryCount: Int,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): PaymentOrder = PaymentOrder(
            paymentOrderId,
            paymentId,
            sellerId,
            amount,
            status,
            retryCount,
            createdAt,
            updatedAt
        )
    }
}

