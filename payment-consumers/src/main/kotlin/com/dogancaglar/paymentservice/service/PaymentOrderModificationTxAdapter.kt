package com.dogancaglar.paymentservice.service

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderStatusCheckRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class PaymentOrderModificationTxAdapter(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val clock: Clock
) : PaymentOrderModificationPort {

    @Transactional(timeout = 2)
    override fun markAsCaptured(order: PaymentOrder): PaymentOrder {
        val draft = order.markAsCaptured().withUpdateAt(LocalDateTime.now(clock))
        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)

        return persisted
    }

    @Transactional(timeout = 2)
    override fun markAsCapturePending(order: PaymentOrder): PaymentOrder {
        val draft = order.markCaptureDeclined()
            .incrementRetry()
            .withUpdateAt(LocalDateTime.now(clock))
        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)
        return persisted
    }



    @Transactional(timeout = 2)
    override fun markAsCaptureFailed(order: PaymentOrder): PaymentOrder {
        val draft = order.markCaptureDeclined().withUpdateAt(LocalDateTime.now())
        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)
        return persisted
    }
}


class MissingPaymentOrderException(
    val paymentOrderId: Long,
    message: String = "PaymentOrder row is missing for id=$paymentOrderId"
) : RuntimeException(message)