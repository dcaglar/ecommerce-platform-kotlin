package com.dogancaglar.paymentservice.psp

import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class PSPClient(private val simulator: NetworkSimulator) {

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

    fun checkPaymentStatus(paymentOrderId:String): PaymentOrderStatus {
        simulator.simulate();
        val pspResponse = getPaymentStatusResult()
        return PSPStatusMapper.fromPspStatus(pspResponse.status)
    }

    private fun getPaymentResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < 40 -> PaymentOrderStatus.SUCCESSFUL  //final
            roll < 80 -> PaymentOrderStatus.FAILED     //retry payment
            roll < 90 ->PaymentOrderStatus.DECLINED    //final stage
            else      -> PaymentOrderStatus.AUTH_NEEDED //schedule payment status check
        }
        return PSPResponse(result.toString());
    }

    private fun getRetryPaymentResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < 0 -> PaymentOrderStatus.SUCCESSFUL  //final
            roll < 100 -> PaymentOrderStatus.FAILED     //retry payment
            roll < 0 ->PaymentOrderStatus.DECLINED    //final stage
            else      -> PaymentOrderStatus.AUTH_NEEDED //
        }
        return PSPResponse(result.toString());
    }

    private fun getPaymentStatusResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < 50 -> PaymentOrderStatus.CAPTURE_PENDING //schedule a status check for hours later
            roll < 70 ->  PaymentOrderStatus.SUCCESSFUL      // final success
            else ->  PaymentOrderStatus.DECLINED   } //final declibe
        return PSPResponse(result.toString());
    }
}