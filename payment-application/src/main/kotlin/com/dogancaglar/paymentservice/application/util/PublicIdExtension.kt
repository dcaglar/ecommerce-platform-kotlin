package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId


fun PaymentIntentId.toPublicPaymentIntentId(): String =
    PublicIdFactory.publicPaymentIntentId(this.value)


fun PaymentId.toPublicPaymentId(): String =
    PublicIdFactory.publicPaymentId(this.value)

fun PaymentOrderId.toPublicPaymentOrderId(): String =
    PublicIdFactory.publicPaymentOrderId(this.value)