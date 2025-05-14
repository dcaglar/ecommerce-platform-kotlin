package com.dogancaglar.paymentservice.config.temporal

import com.dogancaglar.paymentservice.adapter.workflow.PaymentStatusActivitiesImpl
import com.dogancaglar.paymentservice.adapter.workflow.PaymentStatusWorkflowImpl
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.psp.PSPClient
import io.temporal.worker.WorkerFactory
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class TemporalWorkerStarter(
    private val workerFactory: WorkerFactory,
    private val pspClient: PSPClient,
    private val paymentOrderRepository: PaymentOrderRepository
) {
    @PostConstruct
    fun start() {
        val worker = workerFactory.newWorker("PAYMENT_STATUS_TASK_QUEUE")
        worker.registerWorkflowImplementationTypes(PaymentStatusWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(
            PaymentStatusActivitiesImpl(pspClient, paymentOrderRepository)
        )
        workerFactory.start()
    }
}