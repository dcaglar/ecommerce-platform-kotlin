package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime


@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderPspResultUpdated private constructor(
    override val paymentOrderId: String,
    override val publicPaymentOrderId: String,
    override val paymentId: String,
    override val publicPaymentId: String,
    override val sellerId: String,
    override val amountValue: Long,
    override val currency: String,
    val pspStatus: String,
    val latencyMs: Long,
    override val timestamp: LocalDateTime
) : PaymentOrderEvent() {

    override val eventType = EVENT_TYPE

    override fun deterministicEventId(): String =
        "$publicPaymentOrderId:$eventType:$pspStatus"

    companion object {
        const val EVENT_TYPE = "payment_order_psp_result_updated"

        fun from(
            cmd: PaymentOrderCaptureCommand,
            pspStatus: String,
            latencyMs: Long,
            now: LocalDateTime
        ): PaymentOrderPspResultUpdated =
            PaymentOrderPspResultUpdated(
                paymentOrderId = cmd.paymentOrderId,
                publicPaymentOrderId = cmd.publicPaymentOrderId,
                paymentId = cmd.paymentId,
                publicPaymentId = cmd.publicPaymentId,
                sellerId = cmd.sellerId,
                amountValue = cmd.amountValue,
                currency = cmd.currency,
                pspStatus = pspStatus,
                latencyMs = latencyMs,
                timestamp = now
            )

        @JsonCreator
        internal fun fromJson(
            @JsonProperty("paymentOrderId") pOrderId: String,
            @JsonProperty("publicPaymentOrderId") pubOrderId: String,
            @JsonProperty("paymentId") pId: String,
            @JsonProperty("publicPaymentId") pubPId: String,
            @JsonProperty("sellerId") sellerId: String,
            @JsonProperty("amountValue") amount: Long,
            @JsonProperty("currency") currency: String,
            @JsonProperty("pspStatus") pspStatus: String,
            @JsonProperty("latencyMs") latencyMs: Long,
            @JsonProperty("timestamp") timestamp: LocalDateTime
        ) = PaymentOrderPspResultUpdated(
            pOrderId, pubOrderId, pId, pubPId, sellerId, amount, currency, pspStatus, latencyMs, timestamp
        )
    }
}