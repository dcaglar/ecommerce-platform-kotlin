package com.dogancaglar.paymentservice.adapter.workflow

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentStatusActivities
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
import com.dogancaglar.paymentservice.domain.port.PaymentStatusWorkflow
import io.temporal.workflow.Workflow
import org.slf4j.LoggerFactory
import java.time.Duration

class PaymentStatusWorkflowImpl : PaymentStatusWorkflow{

    private val logger = LoggerFactory.getLogger(PaymentStatusWorkflowImpl::class.java)

    private val pspClient = Workflow.newActivityStub(
        PaymentStatusActivities::class.java,
        PaymentWorkflowConfig.activityOptions
    )

    private val delays = listOf(
        Duration.ofMinutes(1),
        Duration.ofMinutes(2),
        Duration.ofMinutes(3),
        Duration.ofMinutes(5),
        Duration.ofMinutes(10)
    )

    override fun checkStatus(paymentOrderId: String) {
        for ((index, delay) in delays.withIndex()) {
            Workflow.sleep(delay)

            val response = pspClient.checkPaymentStatus(paymentOrderId)
            val mappedStatus = PSPStatusMapper.fromPspStatus(response)

            if (mappedStatus == PaymentOrderStatus.SUCCESSFUL) {
                logger.info("Payment $paymentOrderId successful on attempt ${index + 1}")
                return
            } else if (!PSPStatusMapper.requiresStatusCheck(mappedStatus)) {
                logger.warn("Stopping further status checks for $paymentOrderId. Status: $mappedStatus")
                return
            }

            logger.info("Retrying status check for $paymentOrderId. Attempt ${index + 1}, status: $mappedStatus")
        }

        logger.warn("Max status check attempts reached for $paymentOrderId")
    }
}