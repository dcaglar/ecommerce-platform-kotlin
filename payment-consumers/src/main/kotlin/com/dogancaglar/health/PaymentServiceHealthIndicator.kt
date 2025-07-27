package com.dogancaglar.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class PaymentServiceHealthIndicator : HealthIndicator {

    @Value("\${PAYMENT_SERVICE_HEALTH_URL:http://payment-service:8080/actuator/health}")
    private lateinit var paymentServiceHealthUrl: String

    private val restTemplate = RestTemplate()

    override fun health(): Health {
        return try {
            val response: ResponseEntity<String> =
                restTemplate.getForEntity(paymentServiceHealthUrl, String::class.java)
            if (response.statusCode.is2xxSuccessful && response.body != null && response.body!!.contains("\"status\":\"UP\"")) {
                Health.up().withDetail("payment-service", "UP").build()
            } else {
                Health.down().withDetail("payment-service", "NOT UP").build()
            }
        } catch (e: Exception) {
            Health.down(e).withDetail("payment-service", "UNREACHABLE").build()
        }
    }
}

