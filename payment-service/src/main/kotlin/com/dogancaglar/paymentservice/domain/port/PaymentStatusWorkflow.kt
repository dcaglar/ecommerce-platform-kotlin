package com.dogancaglar.paymentservice.domain.port

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

@WorkflowInterface
interface PaymentStatusWorkflow {
    @WorkflowMethod
    fun checkStatus(paymentOrderId: String)
}