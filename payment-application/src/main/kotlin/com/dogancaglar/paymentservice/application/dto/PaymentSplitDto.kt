package com.dogancaglar.paymentservice.application.dto

import com.dogancaglar.paymentservice.domain.model.payment.BalanceAccountType
import com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * PaymentSplitDto
 *
 * The serialization contract for a single split routing instruction.
 * Used in:
 *  - CreatePaymentIntentDto (inbound API request)
 *  - PaymentAuthorizedEvent (Kafka payload, carries splits across the network
 *    from the Edge Cell to the Central Core so PspResultConsumer can lock the
 *    routing matrix into the Central DB).
 *
 * Jackson @JsonCreator + @JsonProperty annotations guarantee that
 * deserialization is explicit, deterministic, and immune to field-reordering.
 *
 * @param targetAccountType  Canonical enum value identifying the ledger bucket.
 * @param targetEntityId     Identifies the beneficiary entity (seller, platform, etc.).
 * @param amountValue        Amount in the smallest currency unit (e.g., cents).
 * @param currency           ISO 4217 three-letter currency code (e.g., "EUR").
 */
data class PaymentSplitDto @JsonCreator private constructor(
    @JsonProperty("targetAccountType") val targetAccountType: BalanceAccountType,
    @JsonProperty("targetEntityId")    val targetEntityId: String,
    @JsonProperty("amountValue")       val amountValue: Long,
    @JsonProperty("currency")          val currency: String
) {
    companion object {
        fun of(
            targetAccountType: BalanceAccountType,
            targetEntityId: String,
            amountValue: Long,
            currency: String
        ): PaymentSplitDto {
            require(targetEntityId.isNotBlank()) { "targetEntityId must not be blank" }
            require(amountValue > 0) { "amountValue must be positive, but was $amountValue" }
            require(currency.matches(Regex("^[A-Z]{3}$"))) {
                "currency must be a valid ISO 4217 code, but was '$currency'"
            }
            return PaymentSplitDto(targetAccountType, targetEntityId, amountValue, currency)
        }

        /** Convert a domain PaymentSplit to its DTO representation for event serialization. */
        fun fromDomain(split: PaymentSplit): PaymentSplitDto = PaymentSplitDto(
            targetAccountType = split.targetAccountType,
            targetEntityId    = split.targetEntityId,
            amountValue       = split.amount.quantity,
            currency          = split.amount.currency.currencyCode
        )
    }

    /** Rehydrate this DTO back into the domain object, e.g. inside PspResultConsumer. */
    fun toDomain(): PaymentSplit = PaymentSplit.of(
        targetAccountType = targetAccountType,
        targetEntityId    = targetEntityId,
        amount            = Amount.of(amountValue, Currency(currency))
    )
}
