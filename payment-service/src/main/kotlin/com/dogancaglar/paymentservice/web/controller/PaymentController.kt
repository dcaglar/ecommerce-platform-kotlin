package com.dogancaglar.paymentservice.web.controller

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    //domain object
    @PostMapping
    @PreAuthorize("hasAuthority('payment:write')")
    fun createPayment(@Valid @RequestBody request: PaymentRequestDTO): ResponseEntity<PaymentResponseDTO> {
        logger.info("📥 Received payment request for order: ${request.orderId}")
            return ResponseEntity.ok(paymentService.createPayment(request))
    }
}

