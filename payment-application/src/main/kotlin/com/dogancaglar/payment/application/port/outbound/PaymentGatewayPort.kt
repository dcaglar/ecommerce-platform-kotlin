package  com.dogancaglar.payment.application.port.outbound

import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.PaymentOrderStatus

/**
 * Port for Payment Service Provider (PSP) operations.
 * Implementations can be real or simulated.
 */
interface PaymentGatewayPort {
    fun charge(order: PaymentOrder): PaymentOrderStatus
    fun chargeRetry(order: PaymentOrder): PaymentOrderStatus
    fun checkPaymentStatus(paymentOrderId: String): PaymentOrderStatus
}