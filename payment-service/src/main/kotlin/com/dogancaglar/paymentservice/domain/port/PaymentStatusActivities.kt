package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.psp.PSPResponse
import java.util.UUID

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod

@ActivityInterface
interface PaymentStatusActivities {

    @ActivityMethod
    fun checkPaymentStatus(paymentOrderId: String): String

    @ActivityMethod
    fun markAsPaid(paymentOrderId: String)

    @ActivityMethod
    fun markAsFailed(paymentOrderId: String)
}