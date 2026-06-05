package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.common.time.Utc
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class InternalTransferRequested(
    override val paymentIntentId: String,
    override val publicPaymentIntentId: String,
    val sourceAccountType: String,
    val sourceAccountId: String,
    val targetAccountType: String,
    val targetEntityId: String,
    override val amountValue: Long,
    override val currency: String,
    override val timestamp: Instant = Utc.nowInstant()
) : PaymentBaseEvent(paymentIntentId, publicPaymentIntentId, amountValue, currency, timestamp) {

    override val eventType = EVENT_TYPE

    override fun deterministicEventId(): String =
        "$publicPaymentIntentId:$targetEntityId:$eventType"

    companion object {
        const val EVENT_TYPE = EventType.INTERNAL_TRANSFER_REQUESTED

        fun from(
            paymentIntentId: String,
            publicPaymentIntentId: String,
            sourceAccountType: String,
            sourceAccountId: String,
            targetAccountType: String,
            targetEntityId: String,
            amountValue: Long,
            currency: String,
            now: Instant = Utc.nowInstant()
        ): InternalTransferRequested {
            return InternalTransferRequested(
                paymentIntentId = paymentIntentId,
                publicPaymentIntentId = publicPaymentIntentId,
                sourceAccountType = sourceAccountType,
                sourceAccountId = sourceAccountId,
                targetAccountType = targetAccountType,
                targetEntityId = targetEntityId,
                amountValue = amountValue,
                currency = currency,
                timestamp = now
            )
        }
    }
}
