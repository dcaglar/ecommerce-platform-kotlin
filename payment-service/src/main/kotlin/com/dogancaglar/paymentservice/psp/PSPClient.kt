package com.dogancaglar.paymentservice.psp

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import org.springframework.stereotype.Component
import java.util.concurrent.TimeoutException
import kotlin.random.Random

@Component
class PSPClient {

    fun charge(order: PaymentOrder): PSPResponse {
        maybeSimulateTimeout()
        val simulatedStatusCode = simulateRandomStatus()
        val mappedStatus = PSPStatusMapper.fromPspStatus(simulatedStatusCode)

        return PSPResponse(simulatedStatusCode)
    }

    fun chargeRetry(order: PaymentOrder): PSPResponse {
        maybeSimulateTimeout()
        val simulatedStatusCode = simulateRandomStatus()
        val mappedStatus = PSPStatusMapper.fromPspStatus(simulatedStatusCode)

        return PSPResponse(simulatedStatusCode)
    }

    private fun simulateRandomStatus(): String {
        return listOf(
            "SUCCESS",  "PENDING",
            "AUTH_NEEDED", "CAPTURE_PENDING", "UNKNOWN"
        ).random()
    }
    fun checkPaymentStatus(paymentOrderId:String): PSPResponse {
        val simulatedStatusCode = simulateRandomStatus()
        val mappedStatus = PSPStatusMapper.fromPspStatus(simulatedStatusCode)

        return PSPResponse(simulatedStatusCode)
    }


    private fun maybeSimulateTimeout() {
        val timeoutProbability = 0.2 // 20% chance
        if (Random.nextDouble() < timeoutProbability) {
            throw TimeoutException("Simulated PSP timeout")
        }
    }
}