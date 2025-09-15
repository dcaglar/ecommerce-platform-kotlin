package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested

data class RetryItem(
    val envelope: EventEnvelope<PaymentOrderPspCallRequested>,
    val raw: ByteArray
)