package com.dogancaglar.paymentservice.adapter.inbound.rest.mapper


import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.AuthorizationRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentMethodDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentResponseDTO
import com.dogancaglar.paymentservice.application.util.toPublicPaymentIntentId
import com.dogancaglar.paymentservice.domain.commands.AuthorizePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.commands.CreatePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentMethod
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import java.time.format.DateTimeFormatter

object PaymentRequestMapper {
    fun toCreatePaymentIntentCommand(dto: CreatePaymentIntentRequestDTO): CreatePaymentIntentCommand =
        CreatePaymentIntentCommand(
            orderId = OrderId(dto.orderId),
            buyerId = BuyerId(dto.buyerId),
            totalAmount = Amount.of(dto.totalAmount.quantity, Currency(dto.totalAmount.currency.name)),
            paymentOrderLines = dto.paymentOrders.map {
                PaymentOrderLine(
                    SellerId(it.sellerId),
                    Amount.of(it.amount.quantity, Currency(it.amount.currency.name))
                )
            }
        )

    fun toAuthorizePaymentIntentCommand(publicPaymentIntentId:String, dto: AuthorizationRequestDTO): AuthorizePaymentIntentCommand =
        AuthorizePaymentIntentCommand(
            paymentIntentId = PaymentIntentId(PublicIdFactory.toInternalId(publicPaymentIntentId)),
            paymentMethod = toPaymentMethod(dto.paymentMethod)
        )





    fun toPaymentResponseDto(paymentIntent: PaymentIntent): CreatePaymentIntentResponseDTO {
        return CreatePaymentIntentResponseDTO(
            paymentIntentId = paymentIntent.paymentIntentId.toPublicPaymentIntentId(),
            status = paymentIntent.status.name,
            buyerId = paymentIntent.buyerId.value,
            orderId = paymentIntent.orderId.value,
            totalAmount = AmountMapper.toDto(paymentIntent.totalAmount),
            createdAt = paymentIntent.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }

    fun toPaymentMethod(dto: PaymentMethodDTO): PaymentMethod =
        when (dto) {
            is PaymentMethodDTO.CardToken ->
                PaymentMethod.CardToken(
                    token = dto.token,
                    cvc = dto.cvc
                )
        }



}