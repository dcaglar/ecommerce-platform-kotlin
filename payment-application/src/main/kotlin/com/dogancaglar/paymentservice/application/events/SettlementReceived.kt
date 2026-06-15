package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.common.event.Event

data class SettlementReceived(
    override val paymentIntentId: String,
    override val publicPaymentIntentId: String,
    val merchantAccount: String,
    val grossAmountValue: Long,
    val netCashAmountValue: Long,
    val pspFeeAmountValue: Long,
    override val currency: String
) : Event, PaymentBaseEvent(
    paymentIntentId = paymentIntentId,
    publicPaymentIntentId = publicPaymentIntentId,
    amountValue = grossAmountValue,
    currency = currency
) {
    override val eventType: String = EventType.SETTLEMENT_RECEIVED
}
