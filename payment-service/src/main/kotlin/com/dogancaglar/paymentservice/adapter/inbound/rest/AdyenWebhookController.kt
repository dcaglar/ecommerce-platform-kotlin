package com.dogancaglar.paymentservice.adapter.inbound.rest

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest

data class AdyenWebhookPayload(
    val eventCode: String,
    val success: String,
    val originalReference: String,
    val merchantAccountCode: String,
    val amount: AmountPayload
)

data class AmountPayload(
    val value: Long,
    val currency: String
)

@RestController
@RequestMapping("/api/v1/webhooks")
class AdyenWebhookController {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/adyen")
    fun handleAdyenWebhook(
        request: HttpServletRequest,
        @RequestBody payload: AdyenWebhookPayload
    ): ResponseEntity<String> {
        logger.info("📥 Received Adyen webhook: eventCode=${payload.eventCode}, success=${payload.success}, originalReference=${payload.originalReference}")
        // TODO: Implement Adyen webhook handling
        return ResponseEntity.ok("[accepted]")
    }
}
