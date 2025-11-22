package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentResponseDTO
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class PaymentController(
    private val paymentService: PaymentService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Create a new payment.
     * 
     * Requires 'payment:write' authority.
     * 
     * @param request Payment request containing order details and payment orders
     * @return ResponseEntity with 201 Created status and PaymentResponseDTO
     */
    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('payment:write')")
    fun createPayment(@Valid @RequestBody request: PaymentRequestDTO): ResponseEntity<PaymentResponseDTO> {
        logger.debug("📥 Sending payment request for order: ${request.orderId}")
        val responseDTO = paymentService.createPayment(request)
        logger.debug("📥 Received payment request for order: ${responseDTO.orderId}, payment id is ${responseDTO.paymentId}")
        
        // Return 201 Created with Location header (best practice for resource creation)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header("Location", "/api/v1/payments/${responseDTO.paymentId}")
            .body(responseDTO)
    }


    @PostMapping("/payments/{paymentId}/captures")
    @PreAuthorize("hasAuthority('payment:write')")
    fun capturePayment(@Valid @RequestBody request: PaymentRequestDTO): ResponseEntity<PaymentResponseDTO> {
        logger.debug("📥 Sending payment request for order: ${request.orderId}")
        val responseDTO = paymentService.createPayment(request)
        logger.debug("📥 Received payment request for order: ${responseDTO.orderId}, payment id is ${responseDTO.paymentId}")

        // Return 201 Created with Location header (best practice for resource creation)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header("Location", "/api/v1/payments/${responseDTO.paymentId}")
            .body(responseDTO)
    }
}