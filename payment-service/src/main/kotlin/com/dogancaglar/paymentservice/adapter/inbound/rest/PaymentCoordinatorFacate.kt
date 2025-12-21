package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.adapter.outbound.persistence.OutboxOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentIntentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentOrderOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.OutboxEventMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentIntentMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentOrderEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentOrderMapper
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentTransactionalFacadePort
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Infrastructure adapter for PaymentOrderModificationPort.
 * Only handles persistence and domain entity mapping - no domain method calls.
 * Application services call domain methods first, then use this adapter to persist.
 * Similar pattern to PaymentOrderOutboundAdapter - Spring auto-wires this when PaymentOrderModificationPort is requested.
 */
@Component
class PaymentCoordinatorFacate(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val paymentRepository:  PaymentRepository,
    private val paymentOrderRepository:  PaymentOrderRepository,
    private val outboxEventRepository: OutboxEventRepository
) : PaymentTransactionalFacadePort {

    @Transactional(transactionManager = "webTxManager", timeout = 2)
    override fun handleAuthorized(authorizedPaymentIntent: PaymentIntent, payment: Payment, paymentOrderList:List<PaymentOrder>,outboxEventList:List<OutboxEvent>){        // Assumes order is already modified by caller (domain method called in application service)
        paymentIntentRepository.updatePaymentIntent(authorizedPaymentIntent)
        paymentRepository.save(payment)
        paymentOrderRepository.insertAll(paymentOrderList)
        outboxEventRepository.saveAll(outboxEventList)

    }
}

class MissingPaymentOrderException(
    val paymentOrderId: Long,
    message: String = "PaymentOrder row is missing for id=$paymentOrderId"
) : RuntimeException(message)
