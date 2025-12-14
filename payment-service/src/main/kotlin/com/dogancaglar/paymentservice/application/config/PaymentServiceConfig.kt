package com.dogancaglar.paymentservice.application.config

import com.dogancaglar.paymentservice.adapter.outbound.id.SnowflakeIdGeneratorAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.OutboxOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentIntentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.serialization.JacksonSerializationAdapter
import com.dogancaglar.paymentservice.application.usecases.AccountBalanceReadService
import com.dogancaglar.paymentservice.application.usecases.AuthorizePaymentIntentService
import com.dogancaglar.paymentservice.application.usecases.CreatePaymentIntentService
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceReadUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.ports.outbound.PspAuthGatewayPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PaymentServiceConfig {



    @Bean
    fun paymentOrderDomainEventMapper(): PaymentOrderDomainEventMapper =
        PaymentOrderDomainEventMapper()


    @Bean
    fun authorizePaymentService(
        paymentIntentRepository: PaymentIntentOutboundAdapter,
        outboxOutboundAdapter: OutboxOutboundAdapter,
        serializationPort: JacksonSerializationAdapter,
        paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
        pspAuthGatewayPort: PspAuthGatewayPort
    ): AuthorizePaymentIntentService {
        return AuthorizePaymentIntentService(
            paymentIntentRepository = paymentIntentRepository,
            psp = pspAuthGatewayPort,
            outboxEventPort = outboxOutboundAdapter,
            serializationPort = serializationPort,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper
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