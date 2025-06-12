package com.dogancaglar.paymentservice.psp

import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class PSPClient(
    val simulator: NetworkSimulator, val config: PspSimulationProperties
) {

    val logger = LoggerFactory.getLogger(javaClass)

    private val active: PspSimulationProperties.ScenarioConfig
        get() = config.scenarios[config.scenario]
            ?: throw IllegalStateException("No scenario config for ${config.scenario}")

    fun charge(order: PaymentOrder): PaymentOrderStatus {
        simulator.simulate();
        val pspResponse = getPaymentResult()
        return PSPStatusMapper.fromPspStatus(pspResponse.status)
    }

    fun chargeRetry(order: PaymentOrder): PaymentOrderStatus {
        simulator.simulate();
        val pspResponse = getRetryPaymentResult()
        return PSPStatusMapper.fromPspStatus(pspResponse.status)
    }

    fun checkPaymentStatus(paymentOrderId: String): PaymentOrderStatus {
        simulator.simulate();
        val pspResponse = getPaymentStatusResult()
        return PSPStatusMapper.fromPspStatus(pspResponse.status)
    }

    private fun getPaymentResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < active.response.successful -> PaymentOrderStatus.SUCCESSFUL  //final
            roll < active.response.successful + active.response.retryable -> PaymentOrderStatus.FAILED     //retry payment
            roll < active.response.successful + active.response.retryable + active.response.nonRetryable -> PaymentOrderStatus.DECLINED    //final stage
            else -> PaymentOrderStatus.PENDING
        }
        return PSPResponse(result.toString());
    }

    private fun getRetryPaymentResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < 50 -> PaymentOrderStatus.SUCCESSFUL  //final
            roll < 90 -> PaymentOrderStatus.FAILED     //retry payment
            roll < 95 -> PaymentOrderStatus.DECLINED    //final stage
            else -> PaymentOrderStatus.PSP_UNAVAILABLE //retryable
        }
        return PSPResponse(result.toString());
    }

    private fun getPaymentStatusResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < 50 -> PaymentOrderStatus.CAPTURE_PENDING //schedule a status check for hours later
            roll < 70 -> PaymentOrderStatus.SUCCESSFUL      // final success
            else -> PaymentOrderStatus.DECLINED
        } //final declibe
        return PSPResponse(result.toString());
    }

    @PostConstruct
    fun log(): Unit {
        logger.info("PspClient initialized with scenario: ${config.scenario}")
    }
}