package com.dogancaglar.paymentservice.service

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentOrderModificationTxAdapter(
    private val paymentOrderRepository: PaymentOrderRepository,
) : PaymentOrderModificationPort {

    @Transactional(timeout = 2)
    override fun markAsCaptured(order: PaymentOrder): PaymentOrder {
        val draft = order.markAsCaptured().withUpdateAt(Utc.nowLocalDateTime())
        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)

        return persisted
    }

    @Transactional(timeout = 2, readOnly = true)
    override fun findByPaymentOrderId(paymentOrderId: PaymentOrderId): PaymentOrder {
        return paymentOrderRepository.findByPaymentOrderId(paymentOrderId)
            .firstOrNull()?: throw MissingPaymentOrderException(paymentOrderId.value)
    }


    @Transactional(timeout = 2)
    override fun markAsCapturePending(order: PaymentOrder): PaymentOrder {
        val draft = order.markCapturePending()
            .incrementRetry()
            .withUpdateAt(Utc.nowLocalDateTime())
        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)
        return persisted
    }



    @Transactional(timeout = 2)
    override fun markAsCaptureFailed(order: PaymentOrder): PaymentOrder {
        val draft = order.markCaptureDeclined().withUpdateAt(Utc.nowLocalDateTime())
        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)
        return persisted
    }

    @Transactional(timeout = 2)
    override fun markAsCaptureRequested(paymentOrderId: Long): PaymentOrder {
        val persisted = paymentOrderRepository.updateReturningIdempotentInitialCaptureRequest(paymentOrderId,
            Utc.nowLocalDateTime())
            ?: throw MissingPaymentOrderException(paymentOrderId)
        return persisted
    }
}


class MissingPaymentOrderException(
    val paymentOrderId: Long,
    message: String = "PaymentOrder row is missing for id=$paymentOrderId"
) : RuntimeException(message)