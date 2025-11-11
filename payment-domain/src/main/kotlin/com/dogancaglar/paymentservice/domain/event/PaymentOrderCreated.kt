package com.dogancaglar.paymentservice.domain.event

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Domain event published when a payment order is created.
 * 
 * This event is only created through the factory method to ensure invariants are maintained.
 * The @JsonCreator annotation allows Jackson to deserialize from JSON (e.g., Kafka messages).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderCreated private @JsonCreator constructor(
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
    @JsonProperty("retryCount") override val retryCount: Int = 0,
) : PaymentOrderEvent {
    
    companion object {
        /**
         * Factory method to create PaymentOrderCreated event.
         * 
         * @param paymentOrderId Internal payment order ID
         * @param publicPaymentOrderId Public payment order ID
         * @param paymentId Internal payment ID
         * @param publicPaymentId Public payment ID
         * @param sellerId Seller/merchant ID
         * @param amountValue Amount in minor currency units
         * @param currency Currency code
         * @param status Payment order status
         * @param createdAt Timestamp when payment order was created
         * @param updatedAt Timestamp when payment order was last updated
         * @param retryCount Number of retry attempts (default: 0)
         * @param retryReason Reason for retry (optional)
         * @param lastErrorMessage Last error message (optional)
         * @return PaymentOrderCreated event instance
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
            createdAt: LocalDateTime = LocalDateTime.now(),
            updatedAt: LocalDateTime = LocalDateTime.now(),
            retryCount: Int = 0
        ): PaymentOrderCreated {
            return PaymentOrderCreated(
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
            )
        }
    }
}