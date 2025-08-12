// payment-infrastructure (or adapter.outbound.persistance)
package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatusCheck
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderStatePort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderStatusCheckRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class PaymentOrderStateTxAdapter(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val statusCheckRepo: PaymentOrderStatusCheckRepository,
    private val clock: Clock
) : PaymentOrderStatePort {

    @Transactional
    override fun markPaid(order: PaymentOrder): PaymentOrder {
        val updated = order.markAsPaid().withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderRepository.save(updated)
        return updated
    }

    @Transactional
    override fun markFailedForRetry(order: PaymentOrder, reason: String?, lastError: String?): PaymentOrder {
        val updated = order.markAsFailed().withRetryReason(reason).withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderRepository.save(updated)
        return updated
    }

    @Transactional
    override fun markPendingAndScheduleStatusCheck(order: PaymentOrder, reason: String?, lastError: String?) {
        val updated = order.markAsPending().incrementRetry().withRetryReason(reason).withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderRepository.save(updated)
        statusCheckRepo.save(
            PaymentOrderStatusCheck.createNew(updated.paymentOrderId.value, LocalDateTime.now(clock).plusMinutes(30))
        )
    }

    @Transactional
    override fun markFinalFailed(order: PaymentOrder, reason: String?): PaymentOrder {
        val updated = order.markAsFinalizedFailed().withRetryReason(reason).withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderRepository.save(updated)
        return updated
    }
}