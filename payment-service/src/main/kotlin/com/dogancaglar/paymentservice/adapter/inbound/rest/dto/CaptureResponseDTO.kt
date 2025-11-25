package com.dogancaglar.paymentservice.adapter.inbound.rest.dto

import com.dogancaglar.port.out.web.dto.AmountDto

data class CaptureResponseDTO(
    val sellerId: String,
    val amount: AmountDto,
    val paymentId:String,
    val paymentOrderId:String,
    val status: String,                         // ✅ Added
    val createdAt: String,
)