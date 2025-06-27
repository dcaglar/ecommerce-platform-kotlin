package  com.dogancaglar.payment.application.port.outbound

import com.dogancaglar.payment.domain.model.PaymentOrderStatusCheck
import java.time.LocalDateTime

interface PaymentOrderStatusCheckRepository {
    fun save(paymentOrderStatusCheck: PaymentOrderStatusCheck)
    fun findDueStatusChecks(now: LocalDateTime): List<PaymentOrderStatusCheck>
    fun markAsProcessed(id: Long)
}