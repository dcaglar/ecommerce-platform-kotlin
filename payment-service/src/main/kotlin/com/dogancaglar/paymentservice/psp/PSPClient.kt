package com.dogancaglar.paymentservice.psp

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import org.springframework.stereotype.Component
import java.util.*
import kotlin.concurrent.thread

@Component
class PSPClient {

    private val random = Random()

    fun charge(paymentOrder: PaymentOrder): PSPResponse {
        simulateDelay()

        val roll = random.nextDouble()

        return when {
            roll < 0.1 -> throw RuntimeException("PSP service unavailable")
            roll < 0.2 -> {
                Thread.sleep(5000) // simulate timeout
                PSPResponse("FAILED")
            }
            roll < 0.9 -> PSPResponse("SUCCESS")
            else -> PSPResponse("FAILED")
        }
    }

    private fun simulateDelay() {
        try {
            Thread.sleep((300 + random.nextInt(700)).toLong()) // Random delay between 300ms and 1s
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}