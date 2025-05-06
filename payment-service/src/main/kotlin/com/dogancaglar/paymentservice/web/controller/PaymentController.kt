package com.dogancaglar.paymentservice.web.controller

import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.web.mapper.PaymentMapper
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @PostMapping
    fun createPayment(@RequestBody request: PaymentRequestDTO): ResponseEntity<PaymentResponseDTO> {
        val payment = PaymentMapper.toDomain(request)           // ✅ Transform here
        val result = paymentService.createPayment(payment)      // Pass domain to service
        return ResponseEntity.ok(PaymentMapper.toResponse(result)) // ✅ Transform back to DTO
    }
}
