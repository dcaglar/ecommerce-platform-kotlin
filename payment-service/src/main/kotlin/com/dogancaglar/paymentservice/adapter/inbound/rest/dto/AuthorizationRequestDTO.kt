package com.dogancaglar.paymentservice.adapter.inbound.rest.dto

data class AuthorizationRequestDTO(
    val paymentMethod: PaymentMethodDTO
)