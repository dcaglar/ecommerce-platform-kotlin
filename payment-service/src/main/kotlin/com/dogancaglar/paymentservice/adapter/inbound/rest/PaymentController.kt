package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.port.out.web.dto.PaymentRequestDTO
import com.dogancaglar.port.out.web.dto.PaymentResponseDTO
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(javaClass)

    //domain object
    @PostMapping
    @PreAuthorize("hasAuthority('payment:write')")
    fun createPayment(@Valid @RequestBody request: PaymentRequestDTO): ResponseEntity<PaymentResponseDTO> {
        logger.info("📥 Sending payment request for order: ${request.orderId}")
        val responseDTO = paymentService.createPayment(request);
        logger.info("📥 Received payment request for order: ${responseDTO.orderId} , payment id is  ${responseDTO.paymentId}")
        //todo remember to change  to http 201 or 202
        return ResponseEntity.ok(responseDTO)
    }
}