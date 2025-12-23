// payment-application/src/main/kotlin/.../usecases/CreatePaymentService.kt
package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthGatewayPort

class CreatePaymentIntentService(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val idGeneratorPort: IdGeneratorPort,
    private val psp: PspAuthGatewayPort
    ) : CreatePaymentIntentUseCase {

    override fun create(cmd: CreatePaymentIntentCommand): PaymentIntent {
        val paymentIntentId = PaymentIntentId(idGeneratorPort.nextPaymentIntentId(cmd.buyerId, cmd.orderId))

        val createdPendingPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = cmd.buyerId,
            orderId = cmd.orderId,
            totalAmount = cmd.totalAmount,
            paymentOrderLines = cmd.paymentOrderLines,
        )
        val stripeCreateIdempotencyKey= "create:${createdPendingPaymentIntent.paymentIntentId.value}"
        paymentIntentRepository.save(createdPendingPaymentIntent)
        val createdPaymentIntent = psp.createIntent(stripeCreateIdempotencyKey,createdPendingPaymentIntent);
        if(createdPaymentIntent.pspReference!!.isNotBlank()) {
            paymentIntentRepository.updatePspReference(createdPaymentIntent!!.paymentIntentId.value, createdPaymentIntent.pspReference!!, java.time.Instant.now())
        }
            return  createdPaymentIntent
        }
    }
