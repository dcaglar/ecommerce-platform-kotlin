package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.AuthorizationRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentResponseDTO
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
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
     * @return ResponseEntity with 201,202,200 Created status and PaymentResponseDTO
     */
    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('payment:write')")
    fun createPayment(
        @RequestHeader("Idempotency-Key") idempotencyKey: String?,
        @Valid @RequestBody request: CreatePaymentIntentRequestDTO): ResponseEntity<CreatePaymentIntentResponseDTO> {
        logger.info("📥 Sending payment request for order: ${request.orderId} with idempodencykey: $idempotencyKey")
        require(!idempotencyKey.isNullOrBlank()) {
            "Idempotency-Key header is required"
        }

        val result = idempotencyService.run(idempotencyKey, request) {
            paymentService.createPaymentIntent(request)
        }

        val responseDTO = result.response
        // Return 201/200/202 Created with Location header (best practice for resource creation)
        logger.info("📥 Received payment request for order: ${responseDTO.orderId}, payment id is ${responseDTO.paymentIntentId}")
        return when (result.status) {

            IdempotencyExecutionStatus.CREATED -> ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/v1/payments/${responseDTO.paymentIntentId}")
                .body(responseDTO)

            IdempotencyExecutionStatus.REPLAYED -> ResponseEntity
                .status(HttpStatus.OK)
                .header("Location", "/api/v1/payments/${responseDTO.paymentIntentId}")
                .body(responseDTO)

            IdempotencyExecutionStatus.IN_PROGRESS -> ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .header("Retry-After", "1")
                // use a dummy location for now OR point to idempotency status
                .header("Location", "/api/v1/idempotency/$idempotencyKey")
                .body(responseDTO)
        }
    }

    /**
     * Get payment intent status (for polling when payment is pending)
     * Checks if pspReference exists and retrieves clientSecret from Stripe if available
     */
    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAuthority('payment:write')")
    fun getPaymentIntent(
        @PathVariable("paymentId") publicPaymentId: String
    ): ResponseEntity<CreatePaymentIntentResponseDTO> {
        logger.info("📥 Getting payment intent: {}", publicPaymentId)
        val dto = paymentService.getPaymentIntent(publicPaymentId)
        return ResponseEntity.status(HttpStatus.OK).body(dto)
    }

    /**
     * STEP 2 — Authorize Existing Payment
     */
    @PostMapping("/payments/{paymentId}/authorize")
    @PreAuthorize("hasAuthority('payment:write')")
    fun authorizePayment(
        @PathVariable("paymentId") publicPaymentId: String,
        @Valid @RequestBody request: AuthorizationRequestDTO
    ): ResponseEntity<CreatePaymentIntentResponseDTO> {

        val dto = paymentService.authorizePayment(publicPaymentId,request)

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(dto)
    }


    @PostMapping("/payments/{paymentId}/captures")
    @PreAuthorize("hasAuthority('payment:write')")
    fun capturePayment(@Valid @RequestBody request: CreatePaymentIntentRequestDTO): ResponseEntity<CreatePaymentIntentResponseDTO> {
        logger.debug("📥 Sending payment request for order: ${request.orderId}")
        val responseDTO = paymentService.createPaymentIntent(request)
        logger.debug("📥 Received payment request for order: ${responseDTO.orderId}, payment id is ${responseDTO.paymentIntentId}")

        // Return 201 Created with Location header (best practice for resource creation)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header("Location", "/api/v1/payments/${responseDTO.paymentIntentId}")
            .body(responseDTO)
    }
}