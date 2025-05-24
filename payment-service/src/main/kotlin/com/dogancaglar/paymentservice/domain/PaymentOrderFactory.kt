package com.dogancaglar.paymentservice.domain

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import de.huxhorn.sulky.ulid.ULID
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class PaymentOrderFactory {

    fun create(
        paymentOrderId: Long,
        paymentId: Long,
        sellerId: ULID,
        amount: Amount
    ): PaymentOrder {
        return PaymentOrder(
            paymentId = paymentId.toString(),
            paymentOrderId = paymentOrderId,
            sellerId = sellerId.toString(),
            amount = amount,
            status = PaymentOrderStatus.INITIATED,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
}