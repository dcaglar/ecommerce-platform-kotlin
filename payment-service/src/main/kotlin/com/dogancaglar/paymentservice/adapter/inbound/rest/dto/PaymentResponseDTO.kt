package com.dogancaglar.paymentservice.adapter.inbound.rest.dto

import com.dogancaglar.port.out.web.dto.AmountDto


data class PaymentResponseDTO(
    val paymentId: String,
    val buyerId: String,
    val orderId: String,
    val totalAmount: AmountDto,
    val status: String,                         // ✅ Added
    val createdAt: String,                      // ✅ Added
)