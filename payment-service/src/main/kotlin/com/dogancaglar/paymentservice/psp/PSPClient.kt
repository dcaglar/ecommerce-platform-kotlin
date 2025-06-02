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
            roll < 30 -> "SUCCESSFUL"  //final
            roll < 60 -> "DECLINED"   //retry payment
            roll < 80 -> "CAPTURE_PENDING"  //schedule payment status check
            else      -> "AUTH_NEEDED" //schedule payment status check
        }
        return PSPResponse(result);
    }

    private fun getRetryPaymentResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < 40 -> "SUCCESSFUL"  //final
            roll < 60 -> "DECLINED"   //retry payment
            roll < 80 -> "CAPTURE_PENDING"  //schedule payment status check
            else      -> "AUTH_NEEDED" //schedule payment status check
        }
        return PSPResponse(result);
    }

    private fun getPaymentStatusResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < 40 -> "CAPTURE_PENDING" //schedule a status check for 10 mins later
            roll < 70 -> "DECLINED"       // final
            roll < 90 -> "SUCCESSFUL"    //final
            else      -> "AUTH_NEEDED" //schedule a status check for 10 mins later
        }
        return PSPResponse(result);
    }
}