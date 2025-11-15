package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Domain event published when PSP result is updated for a payment order.
 * 
 * This event is only created through the factory method to ensure invariants are maintained.
 * The @JsonCreator annotation allows Jackson to deserialize from JSON (e.g., Kafka messages).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderPspResultUpdated private @JsonCreator constructor(
    @JsonProperty("paymentOrderId") override val paymentOrderId: String,
    @JsonProperty("publicPaymentOrderId") override val publicPaymentOrderId: String,
    @JsonProperty("paymentId") override val paymentId: String,
    @JsonProperty("publicPaymentId") override val publicPaymentId: String,
    @JsonProperty("sellerId") override val sellerId: String,
    @JsonProperty("amountValue") override val amountValue: Long,
    @JsonProperty("currency") override val currency: String,
    @JsonProperty("status") override val status: String, // last known domain status (from request)
    @JsonProperty("createdAt") override val createdAt: LocalDateTime,
    @JsonProperty("updatedAt") override val updatedAt: LocalDateTime,
    @JsonProperty("retryCount") override val retryCount: Int, // attempt index (0..n)
    // PSP result payload (minimal, enough for Applier to decide)
    @JsonProperty("pspStatus") val pspStatus: String, // maps to PaymentOrderStatus via PSPStatusMapper
    @JsonProperty("pspErrorCode") val pspErrorCode: String? = null,
    @JsonProperty("pspErrorDetail") val pspErrorDetail: String? = null,
    @JsonProperty("latencyMs") val latencyMs: Long? = null
) : PaymentOrderEvent {
    
    companion object {
        /**
         * Factory method to create PaymentOrderPspResultUpdated event.
         * 
         * @param paymentOrderId Internal payment order ID
         * @param publicPaymentOrderId Public payment order ID
         * @param paymentId Internal payment ID
         * @param publicPaymentId Public payment ID
         * @param sellerId Seller/merchant ID
         * @param amountValue Amount in minor currency units
         * @param currency Currency code
         * @param status Payment order status (last known domain status from request)
         * @param createdAt Timestamp when payment order was created
         * @param updatedAt Timestamp when payment order was last updated
         * @param retryCount Number of retry attempts
         * @param pspStatus PSP status (maps to PaymentOrderStatus via PSPStatusMapper)
         * @param retryReason Reason for retry (optional)
         * @param lastErrorMessage Last error message (optional)
         * @param pspErrorCode PSP error code (optional)
         * @param pspErrorDetail PSP error detail (optional)
         * @param latencyMs Latency in milliseconds (optional)
         * @return PaymentOrderPspResultUpdated event instance
         */
        fun create(
            paymentOrderId: String,
            paymentId: String,
            sellerId: String,
            amountValue: Long,
            currency: String,
            status: String,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
            retryCount: Int,
            pspStatus: String,
            pspErrorCode: String? = null,
            pspErrorDetail: String? = null,
            latencyMs: Long? = null
        ): PaymentOrderPspResultUpdated {
            return PaymentOrderPspResultUpdated(
                paymentOrderId = paymentOrderId,
                publicPaymentOrderId = PaymentOrderId(paymentOrderId.toLong()).toPublicPaymentOrderId(),
                paymentId = paymentId,
                publicPaymentId = PaymentId(paymentId.toLong()).toPublicPaymentId(),
                sellerId = sellerId,
                amountValue = amountValue,
                currency = currency,
                status = status,
                createdAt = createdAt,
                updatedAt = updatedAt,
                retryCount = retryCount,
                pspStatus = pspStatus,
                pspErrorCode = pspErrorCode,
                pspErrorDetail = pspErrorDetail,
                latencyMs = latencyMs
            )
        }
    }
}