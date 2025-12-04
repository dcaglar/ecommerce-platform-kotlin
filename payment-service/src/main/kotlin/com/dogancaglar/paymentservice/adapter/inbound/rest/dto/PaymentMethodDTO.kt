package com.dogancaglar.paymentservice.adapter.inbound.rest.dto

sealed class PaymentMethodDTO {

    data class CardToken(
        val token: String,
        val cvc: String? = null // Optional, depending on PSP config
    ) : PaymentMethodDTO()
}