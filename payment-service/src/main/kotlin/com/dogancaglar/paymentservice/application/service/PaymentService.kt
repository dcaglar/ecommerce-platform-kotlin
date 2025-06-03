import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

@Service
class PaymentService(
    @Qualifier("paymentRetryPaymentAdapter") val paymentRetryPaymentAdapter: PaymentRetryPaymentAdapter,
    private val paymentOrderOutboundPort: PaymentOrderOutboundPort,
    private val clock: Clock
) {

    companion object {
        const val MAX_RETRIES = 5
        private val logger: Logger = LoggerFactory.getLogger(PaymentService::class.java)
    }

    fun handleRetryEvent(
        order: PaymentOrder,
        reason: String? = null,
        lastError: String? = null
    ) {
        val retryCount = paymentRetryPaymentAdapter.getRetryCount(order.paymentOrderId)
        val updated = order
            .markAsFailed()
            .withRetryReason(reason)
            .withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))

        if (retryCount < MAX_RETRIES) {
            val backOffExpMillis = computeBackoffDelayMillis(retryCount = retryCount + 1)
            LogContext.withRetryFields(
                retryCount + 1, reason, lastError, backOffExpMillis
            ) {
                val scheduledAt = System.currentTimeMillis().plus(backOffExpMillis)
                logger.info(
                    "Retrying paymentOrderId={} retryCount={} scheduledAt={}",
                    order.publicPaymentOrderId,
                    retryCount + 1,
                    scheduledAt
                )
            }
            paymentRetryPaymentAdapter.scheduleRetry(order, backOffExpMillis)
        } else {
            val finalizedStatus = updated.markAsFinalizedFailed().withLastError("Max retries reached")
                .withUpdatedAt(LocalDateTime.now(clock))
            paymentOrderOutboundPort.save(finalizedStatus)
            paymentRetryPaymentAdapter.resetRetryCounter(order.paymentOrderId)
        }
    }

    private fun computeBackoffDelayMillis(retryCount: Int): Long {
        val baseDelay = 1000L // 1 second
        val maxDelay = 60000L // 1 minute
        return (baseDelay * Math.pow(2.0, (retryCount - 1).toDouble())).toLong().coerceAtMost(maxDelay)
    }
}