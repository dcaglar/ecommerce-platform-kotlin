package com.dogancaglar.paymentservice.adapter.inbound.rest.mapper


import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.AuthorizePaymentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.domain.commands.AuthorizePaymentCommand
import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import java.time.format.DateTimeFormatter

object PaymentRequestMapper {
    fun toCommand(dto: CreatePaymentRequestDTO): CreatePaymentCommand =
        CreatePaymentCommand(
            orderId = OrderId(dto.orderId),
            buyerId = BuyerId(dto.buyerId),
            totalAmount = Amount.of(dto.totalAmount.quantity, Currency(dto.totalAmount.currency.name)),
            paymentLines = dto.paymentOrders.map {
                PaymentLine(
                    SellerId(it.sellerId),
                    Amount.of(it.amount.quantity, Currency(it.amount.currency.name))
                )
            }
        )


    fun toCommand(dto: AuthorizePaymentRequestDTO): AuthorizePaymentCommand =
        AuthorizePaymentCommand(
            paymentId = PaymentId(PublicIdFactory.toInternalId(dto.paymentId)
        ))


    fun toResponse(domain: Payment): PaymentResponseDTO {
        return PaymentResponseDTO(
            paymentId = domain.paymentId.toPublicPaymentId(),
            status = domain.status.name,
            buyerId = domain.buyerId.value,
            orderId = domain.orderId.value,
            totalAmount = AmountMapper.toDto(domain.totalAmount),
            createdAt = domain.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }

}