package com.dogancaglar.paymentservice.application.commands

import com.dogancaglar.paymentservice.application.events.PaymentOrderCommand
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderCaptureCommand private constructor(
    override val paymentOrderId: String,
    override val publicPaymentOrderId: String,
    override val paymentId: String,
    override val publicPaymentId: String,
    override val sellerId: String,
    override val amountValue: Long,
    override val currency: String,
    val attempt: Int,
    override val timestamp: Instant
) : PaymentOrderCommand() {

    override val eventType = EVENT_TYPE

    override fun deterministicEventId(): String =
        "$publicPaymentOrderId:$eventType:$attempt"

    companion object {
        const val EVENT_TYPE = "payment_order_capture_requested"

        fun from(order: PaymentOrder, now: Instant,attempt: Int): PaymentOrderCaptureCommand {
            require(order.status == PaymentOrderStatus.CAPTURE_REQUESTED || order.status== PaymentOrderStatus.PENDING_CAPTURE ){
                "Invalid PaymentOrderCommandGeneration creation, payment order status was ${order.status.name}"
            }
            return PaymentOrderCaptureCommand(
                paymentOrderId = order.paymentOrderId.value.toString(),
                publicPaymentOrderId = order.paymentOrderId.toPublicPaymentOrderId(),
                paymentId = order.paymentId.value.toString(),
                publicPaymentId = order.paymentId.toPublicPaymentId(),
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
        ) = PaymentOrderCaptureCommand(
            pOrderId, pubOrderId, pId, pubPId, sellerId, amount, currency, attempt, timestamp
        )
    }
}
