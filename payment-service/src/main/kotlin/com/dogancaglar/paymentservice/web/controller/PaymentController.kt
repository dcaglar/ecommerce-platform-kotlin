package com.dogancaglar.paymentservice.web.controller

import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.web.mapper.PaymentRequestMapper
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.util.UUID

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    private val logger = LoggerFactory.getLogger(javaClass)


    @PostMapping
    @PreAuthorize("hasAuthority('payment:write')")
    fun createPayment(@Valid @RequestBody request: PaymentRequestDTO): ResponseEntity<PaymentResponseDTO> {
        val traceId = UUID.randomUUID().toString()
        MDC.put("traceId", traceId)
        try {
            logger.info("Starting with paymentId $traceId")
            val result =
                paymentService.createPayment(PaymentRequestMapper.toDomain(request))      // Pass domain to service
            return ResponseEntity.ok(PaymentRequestMapper.toResponse(result)) // ✅ Transform back to DTO
        } catch (e: Exception) {
            logger.error("Something went wrong", e);
            return ResponseEntity.internalServerError().build()
        } finally {
            MDC.clear()
        }
    }
}

