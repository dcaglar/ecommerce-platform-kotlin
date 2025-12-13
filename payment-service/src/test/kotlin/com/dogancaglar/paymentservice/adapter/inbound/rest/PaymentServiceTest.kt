package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.validator.PaymentValidator
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.port.out.web.dto.AmountDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentOrderLineDTO
import com.dogancaglar.paymentservice.application.util.toPublicPaymentIntentId
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentIntentUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PaymentServiceTest {

    private lateinit var createPaymentIntentUseCase: CreatePaymentIntentUseCase
    private lateinit var authorizePaymentIntentUseCase: AuthorizePaymentIntentUseCase
    private lateinit var paymentService: PaymentService
    private lateinit var paymentValidator: PaymentValidator

    @BeforeEach
    fun setUp() {
        createPaymentIntentUseCase = mockk()
        paymentValidator = mockk(relaxed = true)
        paymentService = PaymentService( authorizePaymentIntentUseCase,createPaymentIntentUseCase, paymentValidator)
    }
}