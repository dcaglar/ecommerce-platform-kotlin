package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.AuthorizationRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.PaymentRequestMapper
import com.dogancaglar.paymentservice.application.validator.PaymentValidator
import com.dogancaglar.paymentservice.domain.commands.GetPaymentIntentCommand
import com.dogancaglar.paymentservice.domain.commands.ProcessPaymentIntentUpdateCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentIntentUseCase
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentResponseDTO
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.inbound.GetPaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.inbound.ProcessPaymentIntentUpdateUseCase
import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val authorizePaymentIntentUseCase: AuthorizePaymentIntentUseCase,
    private val createPaymentIntentUseCase: CreatePaymentIntentUseCase,
    private val getPaymentIntentUseCase: GetPaymentIntentUseCase,
    private val processPaymentIntentUpdateUseCase: ProcessPaymentIntentUpdateUseCase,
    private val paymentValidator: PaymentValidator,
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    fun createPaymentIntent(request: CreatePaymentIntentRequestDTO): CreatePaymentIntentResponseDTO {
        paymentValidator.validate(request)
        val cmd = PaymentRequestMapper.toCreatePaymentIntentCommand(request)
        //only persist to db.
        val paymentIntent = createPaymentIntentUseCase.create(cmd)
        return PaymentRequestMapper.toPaymentResponseDto(paymentIntent)
    }

    fun authorizePayment(publicPaymentId:String,request: AuthorizationRequestDTO): CreatePaymentIntentResponseDTO {
        val cmd = PaymentRequestMapper.toAuthorizePaymentIntentCommand(publicPaymentId,request)
        val paymentIntent = authorizePaymentIntentUseCase.authorize(cmd)
        return PaymentRequestMapper.toPaymentResponseDto(paymentIntent)
    }

    fun getPaymentIntent(publicPaymentIntentId: String): CreatePaymentIntentResponseDTO {
        val internalId = PublicIdFactory.toInternalId(publicPaymentIntentId)
        val paymentIntentId = PaymentIntentId(internalId)
        val cmd = GetPaymentIntentCommand(paymentIntentId)
        val paymentIntent = getPaymentIntentUseCase.getPaymentIntent(cmd)
        return PaymentRequestMapper.toPaymentResponseDto(paymentIntent)
    }

    @org.springframework.transaction.annotation.Transactional
    fun processWebhook(event: Event) {
        val dataObjectDeserializer = event.dataObjectDeserializer
        val stripeObject = if (dataObjectDeserializer.`object`.isPresent) {
            dataObjectDeserializer.`object`.get()
        } else {
            logger.error("Failed to deserialize Stripe object for event: ${event.id}")
            return
        }

        when (event.type) {
            "payment_intent.created" -> {
                if (stripeObject is PaymentIntent) {
                    val metaId = stripeObject.metadata["payment_intent_id"]
                    if (metaId != null) {
                        try {
                            val paymentIntentId = PaymentIntentId(metaId.toLong())
                            val cmd = ProcessPaymentIntentUpdateCommand(
                                paymentIntentId = paymentIntentId,
                                pspReference = stripeObject.id,
                                clientSecret = stripeObject.clientSecret,
                                status = PaymentIntentStatus.CREATED
                            )
                            processPaymentIntentUpdateUseCase.processUpdate(cmd)
                            logger.info("Processed webhook payment_intent.created for ${paymentIntentId.value}")
                        } catch (e: NumberFormatException) {
                            logger.error("Invalid payment_intent_id in metadata: $metaId")
                        }
                    } else {
                        logger.warn("Metadata payment_intent_id missing for event ${event.id}")
                    }
                }
            }
            else -> logger.debug("Unhandled event type: ${event.type}")
        }
    }
}