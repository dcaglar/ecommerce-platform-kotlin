package com.dogancaglar.paymentservice.web.controller

import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.web.mapper.PaymentRequestMapper
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService
){

    @PostMapping
    @PreAuthorize("hasAuthority('payment:write')")
    fun createPayment(@Valid @RequestBody request: PaymentRequestDTO): ResponseEntity<PaymentResponseDTO> {
        val result = paymentService.createPayment(PaymentRequestMapper.toDomain(request))      // Pass domain to service
        return ResponseEntity.ok(PaymentRequestMapper.toResponse(result)) // ✅ Transform back to DTO
    }
}
