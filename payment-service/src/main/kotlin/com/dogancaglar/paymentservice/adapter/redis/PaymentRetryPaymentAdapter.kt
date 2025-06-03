import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component("paymentRetryPaymentAdapter")
open class PaymentRetryPaymentAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) : RetryQueuePort<PaymentOrderRetryRequested> {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val queue = "payment_retry_queue"

    override fun getRetryCount(paymentOrderId: Long): Int {
        val retryKey = "retry:count:$paymentOrderId"
        return redisTemplate.opsForValue().get(retryKey)?.toInt() ?: 0
    }

    override fun scheduleRetry(paymentOrder: PaymentOrder, backOffMillis: Long) {
        val retryKey = "retry:count:${paymentOrder.paymentOrderId}"
        // Atomically increment retry count in Redis
        val retryCount = redisTemplate.opsForValue().increment(retryKey) ?: 1
        // ... envelope creation, scheduling logic unchanged ...
    }

    override fun pollDueRetries(): List<EventEnvelope<PaymentOrderRetryRequested>> {
        TODO("Not yet implemented")
    }

    // ...pollDueRetries, resetRetryCounter as before...
}