package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId

data class CapturePaymentCommand(
    val paymentId: PaymentId,
    val sellerId: SellerId,
    val captureAmount: Amount,
) {
    init {
        require(captureAmount.quantity >= 0) { "Capture amount cannot be negative or 0" }
    }

}