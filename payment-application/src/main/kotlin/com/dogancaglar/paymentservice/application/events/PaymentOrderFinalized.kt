package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.isTerminalPspResponse
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderFinalized private constructor(
    override val paymentOrderId: String,
    override val publicPaymentOrderId: String,
    override val paymentId: String,
    override val publicPaymentId: String,
    override val sellerId: String,
    override val amountValue: Long,
    override val currency: String,
    val status : String,
    override val timestamp: LocalDateTime
) : PaymentOrderEvent() {

    override val eventType = EVENT_TYPE

    override fun deterministicEventId(): String =
        "$publicPaymentOrderId:$eventType"

    companion object {
        const val EVENT_TYPE = "payment_order_finalized"

        fun from(order: PaymentOrder, now: LocalDateTime,status: PaymentOrderStatus) : PaymentOrderFinalized{
            require(status.isTerminalPspResponse()){
                "PaymentOrderFinalized can only be created termnial psp responses, but was ${order.status}"
            }
            return PaymentOrderFinalized(
                paymentOrderId = order.paymentOrderId.value.toString(),
                publicPaymentOrderId = order.paymentOrderId.toPublicPaymentOrderId(),
                paymentId = order.paymentId.value.toString(),
                publicPaymentId = order.paymentId.toPublicPaymentId(),
                sellerId = order.sellerId.value,
                amountValue = order.amount.quantity,
                currency = order.amount.currency.currencyCode,
                status = status.name,
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
            @JsonProperty("status") status: String,
            @JsonProperty("amountValue") amount: Long,
            @JsonProperty("currency") currency: String,
            @JsonProperty("timestamp") timestamp: LocalDateTime
        ) = PaymentOrderFinalized(
            pOrderId, pubOrderId, pId, pubPId, sellerId, amount, currency, status,timestamp
        )
    }
}