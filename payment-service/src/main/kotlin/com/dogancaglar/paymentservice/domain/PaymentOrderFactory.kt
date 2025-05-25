package com.dogancaglar.paymentservice.domain


private class PaymentOrderFactory {

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


@Component
class PaymenFactory {

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