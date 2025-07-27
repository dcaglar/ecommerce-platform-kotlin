package com.dogancaglar.paymentservice.adapter.inbound.rest.mapper


import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.port.out.web.dto.*
import java.time.format.DateTimeFormatter

object PaymentRequestMapper {
    fun toCommand(dto: PaymentRequestDTO): CreatePaymentCommand =
        CreatePaymentCommand(
            orderId = OrderId(dto.orderId),
            buyerId = BuyerId(dto.buyerId),
            totalAmount = Amount(dto.totalAmount.value, dto.totalAmount.currency.name), // or get currency from DTO
            paymentLines = dto.paymentOrders.map {
                PaymentLine(
                    SellerId(it.sellerId),
                    Amount(it.amount.value, it.amount.currency.name)
                )
            }
        )

    fun toResponse(domain: Payment): PaymentResponseDTO {
        return PaymentResponseDTO(
            id = domain.publicPaymentId,// or use requireNotNull(domain.id)
            paymentId = domain.publicPaymentId,
            status = domain.status.name,
            buyerId = domain.buyerId.value,
            orderId = domain.orderId.value,
            totalAmount = AmountMapper.toDto(domain.totalAmount),
            createdAt = domain.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            paymentOrders = domain.paymentOrders.map { toDto(it) }
        )
    }

    private fun toDto(order: PaymentOrder): PaymentOrderResponseDTO {
        return PaymentOrderResponseDTO(
            sellerId = order.sellerId.value,
            amount = AmountDto(order.amount.value, CurrencyEnum.valueOf(order.amount.currency))
        )
    }
}