package com.dogancaglar.paymentservice.service

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatusCheck
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
    private val statusCheckRepo: PaymentOrderStatusCheckRepository,
    private val clock: Clock
) : PaymentOrderModificationPort {

    @Transactional(timeout = 2)
    override fun markPaid(order: PaymentOrder): PaymentOrder {
        val draft = order.markAsPaid().withUpdatedAt(LocalDateTime.now(clock))
        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)

        return persisted
    }

    @Transactional(timeout = 2)
    override fun markFailedForRetry(order: PaymentOrder, reason: String?, lastError: String?): PaymentOrder {
        val draft = order.markAsFailed()
            .incrementRetry()
            .withRetryReason(reason)
            .withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))
        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)
        return persisted
    }

    @Transactional(timeout = 2)
    override fun markPendingAndScheduleStatusCheck(order: PaymentOrder, reason: String?, lastError: String?) {
        val draft = order.markAsPending()
            .incrementRetry()
            .withRetryReason(reason)
            .withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))

        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)
        if (!persisted.isTerminal()) {
            statusCheckRepo.save(
                PaymentOrderStatusCheck.createNew(
                    persisted.paymentOrderId.value,
                    LocalDateTime.now(clock).plusMinutes(30)
                )
            )
        }
    }

    @Transactional(timeout = 2)
    override fun markFinalFailed(order: PaymentOrder, reason: String?): PaymentOrder {
        val draft = order.markAsFinalizedFailed()
            .withRetryReason(reason)
            .withUpdatedAt(LocalDateTime.now(clock))
        val persisted = paymentOrderRepository.updateReturningIdempotent(draft)
            ?: throw MissingPaymentOrderException(order.paymentOrderId.value)
        return persisted
    }
}


class MissingPaymentOrderException(
    val paymentOrderId: Long,
    message: String = "PaymentOrder row is missing for id=$paymentOrderId"
) : RuntimeException(message)