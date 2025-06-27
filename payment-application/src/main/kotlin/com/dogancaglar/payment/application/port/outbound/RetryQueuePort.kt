package  com.dogancaglar.payment.application.port.outbound

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.vo.PaymentOrderId

interface RetryQueuePort<T> {
    fun scheduleRetry(
        paymentOrder: PaymentOrder, backOffMillis: Long, retryReason: String? = "PSP_TIMEOUT",
        lastErrorMessage: String? = "PSP call timed out, retrying"
    )

    fun pollDueRetries(maxBatchSize: Long): List<EventEnvelope<T>>
    fun getRetryCount(paymentOrderId: PaymentOrderId): Int
    fun resetRetryCounter(paymentOrderId: PaymentOrderId)


}