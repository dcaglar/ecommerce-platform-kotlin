package com.dogancaglar.paymentservice.application.command

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import com.dogancaglar.paymentservice.application.events.PaymentCommand

@JsonIgnoreProperties(ignoreUnknown = true)
data class CapturePaymentCommand private constructor(
    val paymentOrderId: String,
    val publicPaymentOrderId: String,
    override val paymentIntentId: String,
    override val publicPaymentIntentId: String,
    val sellerId: String,
    override val amountValue: Long,
    override val currency: String,
    val attempt: Int,
    override val timestamp: Instant
) : PaymentCommand() {

    override val eventType = EVENT_TYPE

    override fun deterministicEventId(): String =
        "$publicPaymentOrderId:$eventType:$attempt"

    companion object {
        const val EVENT_TYPE = "payment_order_capture_requested"

        fun from(order: PaymentOrder, now: Instant,attempt: Int): CapturePaymentCommand {
            require(order.status == PaymentOrderStatus.CAPTURE_REQUESTED || order.status== PaymentOrderStatus.PENDING_CAPTURE ){
                "Invalid PaymentOrderCommandGeneration creation, payment order status was ${order.status.name}"
            }
            return CapturePaymentCommand(
                paymentOrderId = order.paymentOrderId.value.toString(),
                publicPaymentOrderId = order.paymentOrderId.toPublicPaymentOrderId(),
                paymentIntentId = order.paymentId.value.toString(),
                publicPaymentIntentId = order.paymentId.toPublicPaymentId(),
                sellerId = order.sellerId.value,
                amountValue = order.amount.quantity,
                currency = order.amount.currency.currencyCode,
                attempt = attempt,
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
            @JsonProperty("attempt") attempt: Int,
            @JsonProperty("timestamp") timestamp: Instant
        ) = CapturePaymentCommand(
            pOrderId, pubOrderId, pId, pubPId, sellerId, amount, currency, attempt, timestamp
        )
    }
}