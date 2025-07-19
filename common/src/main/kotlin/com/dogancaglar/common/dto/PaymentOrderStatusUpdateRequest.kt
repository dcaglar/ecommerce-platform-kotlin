package com.dogancaglar.common.dto

import com.dogancaglar.common.event.EventEnvelope

data class PaymentOrderStatusUpdateRequest(
    val envelope: EventEnvelope<*>, // or EventEnvelope<PaymentOrderEvent> for tighter typing
    val newStatus: String           // or PaymentOrderStatus
)