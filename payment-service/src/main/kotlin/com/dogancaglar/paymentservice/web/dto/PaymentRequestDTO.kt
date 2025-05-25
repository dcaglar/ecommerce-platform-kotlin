package com.dogancaglar.paymentservice.web.dto

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class PaymentRequestDTO(
    @field:NotBlank
    val buyerId: String,
    @field:NotNull
    @field:Valid
    val totalAmount: AmountDto,
    @field:NotEmpty
    val orderId: String,
    @field:NotEmpty
    @field:Valid
    val paymentOrders: List<PaymentOrderRequestDTO>
)

data class PaymentRequestDto(
    val id: String?,
    val buyerId: String,
    val orderId: String,
    val totalAmount: Amount,
    val status: PaymentStatus,
    val createdAt: LocalDateTime,
    val paymentOrders: List<PaymentOrder>
)