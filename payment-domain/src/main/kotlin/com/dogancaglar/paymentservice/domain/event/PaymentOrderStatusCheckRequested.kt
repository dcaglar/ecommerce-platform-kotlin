package com.dogancaglar.paymentservice.domain

import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Domain event published when a payment order status check is requested.
 * 
 * This event is only created through the factory method to ensure invariants are maintained.
 * The @JsonCreator annotation allows Jackson to deserialize from JSON (e.g., Kafka messages).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderStatusCheckRequested private @JsonCreator constructor(
    @JsonProperty("paymentOrderId") override val paymentOrderId: String,
    @JsonProperty("publicPaymentOrderId") override val publicPaymentOrderId: String,
    @JsonProperty("paymentId") override val paymentId: String,
    @JsonProperty("publicPaymentId") override val publicPaymentId: String,
    @JsonProperty("sellerId") override val sellerId: String,
    @JsonProperty("amountValue") override val amountValue: Long,
    @JsonProperty("currency") override val currency: String,
    @JsonProperty("status") override val status: String,
    @JsonProperty("createdAt") override val createdAt: LocalDateTime = LocalDateTime.now(),
    @JsonProperty("updatedAt") override val updatedAt: LocalDateTime = LocalDateTime.now(),
    @JsonProperty("retryCount") override val retryCount: Int,
    @JsonProperty("retryReason") override val retryReason: String? = null,
    @JsonProperty("lastErrorMessage") override val lastErrorMessage: String? = null
) : PaymentOrderEvent {
    
    companion object {
        /**
         * Factory method to create PaymentOrderStatusCheckRequested event.
         * 
         * @param paymentOrderId Internal payment order ID
         * @param publicPaymentOrderId Public payment order ID
         * @param paymentId Internal payment ID
         * @param publicPaymentId Public payment ID
         * @param sellerId Seller/merchant ID
         * @param amountValue Amount in minor currency units
         * @param currency Currency code
         * @param status Payment order status
         * @param retryCount Number of retry attempts
         * @param createdAt Timestamp when payment order was created
         * @param updatedAt Timestamp when payment order was last updated
         * @param retryReason Reason for retry (optional)
         * @param lastErrorMessage Last error message (optional)
         * @return PaymentOrderStatusCheckRequested event instance
         */
        fun create(
            paymentOrderId: String,
            publicPaymentOrderId: String,
            paymentId: String,
            publicPaymentId: String,
            sellerId: String,
            amountValue: Long,
            currency: String,
            status: String,
            retryCount: Int,
            createdAt: LocalDateTime = LocalDateTime.now(),
            updatedAt: LocalDateTime = LocalDateTime.now(),
            retryReason: String? = null,
            lastErrorMessage: String? = null
        ): PaymentOrderStatusCheckRequested {
            return PaymentOrderStatusCheckRequested(
                paymentOrderId = paymentOrderId,
                publicPaymentOrderId = publicPaymentOrderId,
                paymentId = paymentId,
                publicPaymentId = publicPaymentId,
                sellerId = sellerId,
                amountValue = amountValue,
                currency = currency,
                status = status,
                createdAt = createdAt,
                updatedAt = updatedAt,
                retryCount = retryCount,
                retryReason = retryReason,
                lastErrorMessage = lastErrorMessage
            )
        }
    }
}