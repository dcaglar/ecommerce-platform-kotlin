package com.dogancaglar.paymentservice.adapter.inbound.rest.dto

import com.dogancaglar.port.out.web.dto.AmountDto

data class CaptureResponseDTO(
    val captureId:String,
    val status: String,                         // ✅ Added
    val createdAt: String,
)