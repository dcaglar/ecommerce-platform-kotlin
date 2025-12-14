// payment-application/src/main/kotlin/.../usecases/CreatePaymentService.kt
package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository

class CreatePaymentIntentService(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val idGeneratorPort: IdGeneratorPort
) : CreatePaymentIntentUseCase {

    override fun create(cmd: CreatePaymentIntentCommand): PaymentIntent {
        val paymentIntentId = PaymentIntentId(idGeneratorPort.nextPaymentIntentId(cmd.buyerId, cmd.orderId))

        val paymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = cmd.buyerId,
            orderId = cmd.orderId,
            totalAmount = cmd.totalAmount,
            paymentOrderLines = cmd.paymentOrderLines,
        )

        paymentIntentRepository.save(paymentIntent)
        return paymentIntent
    }
}