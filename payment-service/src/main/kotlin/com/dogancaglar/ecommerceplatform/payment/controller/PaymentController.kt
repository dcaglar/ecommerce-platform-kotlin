package com.dogancaglar.ecommerceplatform.payment.controller

import com.dogancaglar.ecommerceplatform.payment.api.dto.PaymentRequestDTO
import com.dogancaglar.ecommerceplatform.payment.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/payments")
class PaymentController(private val paymentService: PaymentService) {

    /**
     * Endpoint to create a payment.
     * Requires the user to have the 'payment:write' role to access this endpoint.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('payment:write')")
    fun createPayment(@RequestBody paymentRequest: PaymentRequestDTO): ResponseEntity<String> {
        paymentService.createPayment(paymentRequest)
        return ResponseEntity.ok("Payment successfully created.")
    }
}