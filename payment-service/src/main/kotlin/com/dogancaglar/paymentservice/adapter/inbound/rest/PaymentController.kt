package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentResponseDTO
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class PaymentController(
    private val paymentService: PaymentService,
    private val idempotencyService: IdempotencyService

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
    fun createPayment(
        @RequestHeader("Idempotency-Key") idempotencyKey: String?,
        @Valid @RequestBody request: CreatePaymentRequestDTO): ResponseEntity<PaymentResponseDTO> {
        logger.info("📥 Sending payment request for order: ${request.orderId} with idempodencykey: $idempotencyKey")
        require(!idempotencyKey.isNullOrBlank()) {
            "Idempotency-Key header is required"
        }

        val result = idempotencyService.run(idempotencyKey, request) {
            paymentService.createPayment(request)
        }

        val status = when (result.status) {
            IdempotencyExecutionStatus.CREATED -> HttpStatus.CREATED   // 201 first time
            IdempotencyExecutionStatus.REPLAYED -> HttpStatus.OK       // 200 on retry
        }
        val responseDTO = result.response as PaymentResponseDTO

        logger.info("📥 Received payment request for order: ${responseDTO.orderId}, payment id is ${responseDTO.paymentId}")
        
        // Return 201 Created with Location header (best practice for resource creation)
        return ResponseEntity
            .status(status)
            .header("Location", "/api/v1/payments/${responseDTO.paymentId}")
            .body(responseDTO)
    }

    /**
     * STEP 2 — Authorize Existing Payment
     */
    @PostMapping("/{paymentId}/authorize")
    @PreAuthorize("hasAuthority('payment:write')")
    fun authorizePayment(
        @PathVariable paymentId: String
    ): ResponseEntity<PaymentResponseDTO> {

        val dto = paymentService.authorizePayment(paymentId)

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(dto)
    }


    @PostMapping("/payments/{paymentId}/captures")
    @PreAuthorize("hasAuthority('payment:write')")
    fun capturePayment(@Valid @RequestBody request: CreatePaymentRequestDTO): ResponseEntity<PaymentResponseDTO> {
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