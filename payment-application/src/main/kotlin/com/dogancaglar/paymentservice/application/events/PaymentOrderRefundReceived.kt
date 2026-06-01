package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderRefundReceived private constructor(
    val paymentOrderId: String,
    val publicPaymentOrderId: String,
    override val paymentIntentId: String,
    override val publicPaymentIntentId: String,
    val sellerId: String,
    override val amountValue: Long,
    override val currency: String,
    override val timestamp: Instant
) : com.dogancaglar.paymentservice.application.events.PaymentBaseEvent() {

    override val eventType = EVENT_TYPE

    override fun deterministicEventId(): String =
        "$publicPaymentOrderId:$eventType"

    companion object {
        const val EVENT_TYPE = "payment_order_refund_received"

        fun from(order: PaymentOrder, now: Instant): PaymentOrderRefundReceived {
            require(order.status == PaymentOrderStatus.REFUND_RECEIVED)
            return PaymentOrderRefundReceived(
                paymentOrderId = order.paymentOrderId.value.toString(),
                publicPaymentOrderId = order.paymentOrderId.toPublicPaymentOrderId(),
                paymentIntentId = order.paymentId.value.toString(),
                publicPaymentIntentId = order.paymentId.toPublicPaymentId(),
                sellerId = order.sellerId.value,
                amountValue = order.amount.quantity,
                currency = order.amount.currency.currencyCode,
                timestamp = now
            )
        }
        @JsonCreator
        internal fun fromJson(
            @JsonProperty("paymentOrderId") pOrderId: String,
            @JsonProperty("publicPaymentOrderId") pubOrderId: String,
            @JsonProperty("paymentId") pId: String,
            @JsonProperty("publicPaymentId") pubPId: String,
            @JsonProperty("sellerId") sellerId: String,
            @JsonProperty("amountValue") amount: Long,
            @JsonProperty("currency") currency: String,
            @JsonProperty("timestamp") timestamp: Instant
        ) = PaymentOrderRefundReceived(
            pOrderId, pubOrderId, pId, pubPId, sellerId, amount, currency, timestamp
        )
    }
}