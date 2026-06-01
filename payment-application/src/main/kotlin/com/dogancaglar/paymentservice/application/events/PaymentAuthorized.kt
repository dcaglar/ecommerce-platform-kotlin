package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.dto.PaymentSplitDto
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentIntentId
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.payment.PaymentStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * PaymentAuthorized
 *
 * Domain event emitted by the Edge Cell into the outbox immediately after
 * a PSP authorization response is received as AUTHORIZED.
 *
 * CRITICAL TRANSPORT CONTRACT:
 * This event carries the full [splits] array from the Edge Cell to the
 * Central Core Cluster. The PspResultConsumer (Central) reads this event,
 * instantiates the Payment aggregate via [Payment.initializeFromAuthEvent],
 * and persists the splits array into the Central DB. Once written, the
 * splits become immutable for the lifetime of the payment.
 *
 * The [paymentLines] field from the previous schema has been entirely removed.
 * No cart-item, seller-line, or order-line concepts exist in this event.
 *
 * Fields:
 * @param paymentId              Internal Snowflake ID (string-encoded Long).
 * @param publicPaymentId        Public-facing encoded ID for API responses.
 * @param paymentIntentId        Internal Snowflake ID of the originating intent.
 * @param publicPaymentIntentId  Public-facing encoded intent ID.
 * @param buyerId                Identifier of the purchasing party.
 * @param merchantAccountId      Primary merchant-of-record entity identifier.
 * @param processingModel        Routing model string (DIRECT_MERCHANT or MARKETPLACE).
 * @param totalAmountValue       Total amount in smallest currency unit.
 * @param currency               ISO 4217 currency code (e.g., "EUR").
 * @param splits                 Ordered list of split routing instructions.
 *                               Empty for DIRECT_MERCHANT; non-empty for MARKETPLACE.
 * @param timestamp              Event creation timestamp (UTC Instant).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentAuthorized private @JsonCreator constructor(
    @JsonProperty("paymentIntentId")       override val paymentIntentId: String,
    @JsonProperty("publicPaymentIntentId")  override val publicPaymentIntentId: String,
    @JsonProperty("buyerId")               val buyerId: String,
    @JsonProperty("merchantAccountId")     val merchantAccountId: String,
    @JsonProperty("processingModel")       val processingModel: String,
    @JsonProperty("totalAmountValue")       val totalAmountValue: Long,
    @JsonProperty("currency")             override val currency: String,
    @JsonProperty("splits")               val splits: List<PaymentSplitDto>,
    @JsonProperty("authorizedAt")         override val timestamp: Instant = Utc.nowInstant(),
) : PaymentBaseEvent() {

    override val eventType: String
        get() = "payment_authorized"


    override val amountValue: Long
        get() = totalAmountValue

    override fun deterministicEventId(): String =
        "${publicPaymentIntentId}Id:payment_authorized"

    companion object {

        /**
         * Build a [PaymentAuthorized] event from a freshly created PaymentIntent aggregate.
         *
         * The [splits] are sourced directly from Payment.splits which were locked in
         * at [Payment.initializeFromAuthEvent] time, so math consistency is guaranteed.
         */
        fun from(
            paymentIntent: PaymentIntent,
            timestamp: Instant
        ): PaymentAuthorized {
            require(paymentIntent.status == PaymentIntentStatus.AUTHORIZED) {
                "PaymentAuthorized can only be created from AUTHORIZED payment intent, but was ${paymentIntent.status}"
            }
            return PaymentAuthorized(
                paymentIntentId       = paymentIntent.paymentIntentId.value.toString(),
                publicPaymentIntentId = paymentIntent.paymentIntentId.toPublicPaymentIntentId(),
                buyerId               = paymentIntent.buyerId.value,
                merchantAccountId     = paymentIntent.merchantAccountId,
                processingModel       = paymentIntent.processingModel.name,
                totalAmountValue      = paymentIntent.totalAmount.quantity,
                currency              = paymentIntent.totalAmount.currency.currencyCode,
                splits                = paymentIntent.splits.map { PaymentSplitDto.fromDomain(it) },
                timestamp             = timestamp
            )
        }

        @JsonCreator
        internal fun fromJson(
            @JsonProperty("paymentIntentId")       paymentIntentId: String,
            @JsonProperty("publicPaymentIntentId") publicPaymentIntentId: String,
            @JsonProperty("buyerId")               buyerId: String,
            @JsonProperty("merchantAccountId")     merchantAccountId: String,
            @JsonProperty("processingModel")       processingModel: String,
            @JsonProperty("totalAmountValue")      totalAmountValue: Long,
            @JsonProperty("currency")              currency: String,
            @JsonProperty("splits")                splits: List<PaymentSplitDto>,
            @JsonProperty("authorizedAt")          timestamp: Instant = Utc.nowInstant()
        ) = PaymentAuthorized(
            paymentIntentId       = paymentIntentId,
            publicPaymentIntentId = publicPaymentIntentId,
            buyerId               = buyerId,
            merchantAccountId     = merchantAccountId,
            processingModel       = processingModel,
            totalAmountValue      = totalAmountValue,
            currency              = currency,
            splits                = splits,
            timestamp             = timestamp
        )
    }
}