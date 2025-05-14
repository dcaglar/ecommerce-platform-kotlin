package com.dogancaglar.paymentservice.adapter.workflow

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.PaymentStatusActivities
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
import com.dogancaglar.paymentservice.psp.PSPResponse
import io.temporal.spring.boot.ActivityImpl
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@ActivityImpl
@Component
class PaymentStatusActivitiesImpl(
    private val pspClient: PSPClient,
    private val paymentOrderRepository: PaymentOrderRepository
) : PaymentStatusActivities {

    private val logger = LoggerFactory.getLogger(PaymentStatusActivitiesImpl::class.java)

    override fun checkPaymentStatus(paymentOrderId: String): String {
        val response = pspClient.checkPaymentStatus(paymentOrderId)
        val status = PSPStatusMapper.fromPspStatus(response.status)

        val order = paymentOrderRepository.findById(paymentOrderId)
        if (order != null) {
            val updatedOrder = when (status) {
                PaymentOrderStatus.SUCCESSFUL -> order.markAsPaid()
                PaymentOrderStatus.DECLINED,
                PaymentOrderStatus.FAILED -> order.markAsFinalizedFailed()
                else -> order // keep as-is for pending states
            }
            paymentOrderRepository.save(updatedOrder)
            logger.info("Updated payment order $paymentOrderId with PSP status: $status")
        }

        return response.status
    }

    override fun markAsPaid(paymentOrderId: String) {
        val order = paymentOrderRepository.findById(paymentOrderId)
        if (order != null) {
            val updatedOrder = order.markAsPaid()
            paymentOrderRepository.save(updatedOrder)
            logger.info("Marked payment order $paymentOrderId as PAID")
        } else {
            logger.warn("Payment order $paymentOrderId not found when attempting to mark as PAID")
        }
    }

    override fun markAsFailed(paymentOrderId: String) {
        TODO("Not yet implemented")
    }
}