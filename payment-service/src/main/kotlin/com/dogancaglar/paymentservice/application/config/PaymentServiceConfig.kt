package com.dogancaglar.paymentservice.application.config

import com.dogancaglar.paymentservice.adapter.outbound.id.SnowflakeIdGeneratorAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.OutboxOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentIntentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentOrderModificationAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentOrderOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.serialization.JacksonSerializationAdapter
import com.dogancaglar.paymentservice.application.usecases.AccountBalanceReadService
import com.dogancaglar.paymentservice.application.usecases.AuthorizePaymentIntentService
import com.dogancaglar.paymentservice.application.usecases.CreatePaymentIntentService
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceReadUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.PspAuthGatewayPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.Transactional

@Configuration
class PaymentServiceConfig {



    @Bean
    fun paymentOrderDomainEventMapper(): PaymentOrderDomainEventMapper =
        PaymentOrderDomainEventMapper()


    @Bean
    fun authorizePaymentService(
        idGeneratorPort: SnowflakeIdGeneratorAdapter,
        paymentIntentRepository: PaymentIntentOutboundAdapter,
        serializationPort: JacksonSerializationAdapter,
        paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
        pspAuthGatewayPort: PspAuthGatewayPort,
        paymentTransactionalFacadePort : PaymentTransactionalFacadePort
    ): AuthorizePaymentIntentService {
        return AuthorizePaymentIntentService(
            idGeneratorPort = idGeneratorPort,
            paymentIntentRepository = paymentIntentRepository,
            psp = pspAuthGatewayPort,
            serializationPort = serializationPort,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper,
            paymentTransactionalFacadePort = paymentTransactionalFacadePort
        )
    }

    @Bean
    fun createPaymentService(
        idGeneratorPort: SnowflakeIdGeneratorAdapter,
        paymentIntentRepository: PaymentIntentOutboundAdapter
        ): CreatePaymentIntentService{
        return CreatePaymentIntentService(paymentIntentRepository,idGeneratorPort)
    }

    @Bean
    fun accountBalanceReadUseCase(
        cachePort: AccountBalanceCachePort,
        snapshotPort: AccountBalanceSnapshotPort
    ): AccountBalanceReadUseCase {
        return AccountBalanceReadService(
            cachePort = cachePort,
            snapshotPort = snapshotPort
        )
    }
}