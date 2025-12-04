// payment-application/src/main/kotlin/.../usecases/CreatePaymentService.kt
package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository

class CreatePaymentService(
    private val paymentRepository: PaymentRepository,
    private val idGeneratorPort: IdGeneratorPort
) : CreatePaymentUseCase {

    override fun create(cmd: CreatePaymentCommand): Payment {
        val now = Utc.nowLocalDateTime()
        val paymentId = PaymentId(idGeneratorPort.nextPaymentId(cmd.buyerId, cmd.orderId))

        val payment = Payment.createNew(
            paymentId = paymentId,
            buyerId = cmd.buyerId,
            orderId = cmd.orderId,
            totalAmount = cmd.totalAmount,
            paymentLines = cmd.paymentLines,
        )

        paymentRepository.save(payment)
        return payment
    }
}