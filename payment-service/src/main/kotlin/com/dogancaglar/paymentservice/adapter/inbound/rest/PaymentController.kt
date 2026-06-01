package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.AuthorizationRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentResponseDTO
import com.dogancaglar.paymentservice.application.service.IdempotencyExecutionStatus
import com.dogancaglar.paymentservice.application.service.IdempotencyService
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
import com.dogancaglar.paymentservice.application.events.CaptureReceived
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CaptureRequestDTO

@RestController
@RequestMapping("/api/v1")
class PaymentController(
    private val paymentApiOrchestrator: PaymentApiOrchestrator,
    private val idempotencyService: IdempotencyService,
    private val outboxWriterPort: LocalOutboxWriterPort,
    private val idGeneratorPort: com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper
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
        @Valid @RequestBody request: CreatePaymentIntentRequestDTO
    ): ResponseEntity<CreatePaymentIntentResponseDTO> {
        logger.info("📥 Starting payment create intent reqeust")
        require(!idempotencyKey.isNullOrBlank()) {
            "Idempotency-Key header is required"
        }

        val result = idempotencyService.run(
            key = idempotencyKey,
            requestBody = request,
            responseClass = CreatePaymentIntentResponseDTO::class.java, // Arg 3: The type
            idExtractor = { response ->
                // Arg 4: The lambda to get the internal ID for DB storage
                com.dogancaglar.common.id.PublicIdFactory.toInternalId(response.paymentIntentId!!)
            },
            block = {
                // Arg 5: The business logic block
                paymentApiOrchestrator.createPaymentIntent(request)
            }
        )

        val responseDTO = result.response
        // Return 201/200/202 Created with Location header (best practice for resource creation)
        logger.info("📥 Received payment request for order: \${responseDTO.orderId}, payment id is \${responseDTO.paymentIntentId}")
        return when (result.status) {

            IdempotencyExecutionStatus.CREATED -> {
                // Check if payment is pending (Stripe timed out)
                if (responseDTO.status == "CREATED_PENDING") {
                    ResponseEntity
                        .status(HttpStatus.ACCEPTED)  // 202
                        .header("Location", "/api/v1/payments/\${responseDTO.paymentIntentId}")
                        .header("Retry-After", "2")
                        .body(responseDTO)  // clientSecret will be null/empty
                } else {
                    ResponseEntity
                        .status(HttpStatus.CREATED)  // 201
                        .header("Location", "/api/v1/payments/\${responseDTO.paymentIntentId}")
                        .body(responseDTO)  // clientSecret should be present
                }
            }

            IdempotencyExecutionStatus.REPLAYED -> ResponseEntity
                .status(HttpStatus.OK)
                .header("Idempotent-Replayed", "true")
                .header("Location", "/api/v1/payments/\${responseDTO.paymentIntentId}")
                .body(responseDTO)
        }
    }

    /**
     * Get payment intent status (for polling when payment is pending)
     * Checks if pspReference exists and retrieves clientSecret from Stripe if available
     */
    @GetMapping("/payments/{paymentIntentId}")
    @PreAuthorize("hasAuthority('payment:write')")
    fun getPaymentIntent(
        @PathVariable("paymentIntentId") publicPaymentIntentId: String
    ): ResponseEntity<CreatePaymentIntentResponseDTO> {
        logger.debug("📥 Getting payment intent: {}", publicPaymentIntentId)
        val dto = paymentApiOrchestrator.getPaymentIntent(publicPaymentIntentId)
        return ResponseEntity.status(HttpStatus.OK).body(dto)
    }

    @PostMapping("/payments/{paymentIntentId}/authorize")
    @PreAuthorize("hasAuthority('payment:write')")
    fun authorizePayment(
        @PathVariable("paymentIntentId") publicPaymentIntentId: String,
        @Valid @RequestBody request: AuthorizationRequestDTO
    ): ResponseEntity<CreatePaymentIntentResponseDTO> {

        val dto = paymentApiOrchestrator.authorizePayment(publicPaymentIntentId, request)

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(dto)
    }


    @PostMapping("/payments/{paymentIntentId}/captures")
    @PreAuthorize("hasAuthority('payment:write')")
    fun capturePayment(
        @PathVariable("paymentIntentId") publicPaymentIntentId: String,
        @Valid @RequestBody request: CaptureRequestDTO
    ): ResponseEntity<Void> {
        logger.debug("📥 Received capture request for payment: $publicPaymentIntentId")

        val captureEvent = CaptureReceived.from(
            paymentIntentId = "", // Not needed for routing if public ID is known, but better use internal if we had it
            publicPaymentIntentId = publicPaymentIntentId,
            merchantAccountId = request.merchantAccountId,
            amountValue = request.amount.quantity,
            currency = request.amount.currency.name,
            now = Utc.nowInstant()
        )

        val envelope = com.dogancaglar.common.event.EventEnvelopeFactory.envelopeFor(
            traceId = com.dogancaglar.common.logging.EventLogContext.getTraceId(),
            data = captureEvent,
            aggregateId = captureEvent.publicPaymentIntentId,
            parentEventId = com.dogancaglar.common.logging.EventLogContext.getEventId()
        )

        val payload = objectMapper.writeValueAsString(envelope)

        val outboxEvent = OutboxEvent.createNew(
            oeid = idGeneratorPort.nextPaymentId(),
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = payload
        )
        
        outboxWriterPort.save(outboxEvent)

        return ResponseEntity.accepted().build()
    }
}