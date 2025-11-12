package com.dogancaglar.paymentservice.application.config

import com.dogancaglar.paymentservice.adapter.outbound.persistence.OutboxOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.RedisIdGeneratorPortAdapter
import com.dogancaglar.paymentservice.adapter.outbound.serialization.JacksonSerializationAdapter
import com.dogancaglar.paymentservice.application.usecases.AccountBalanceReadService
import com.dogancaglar.paymentservice.application.usecases.AuthorizePaymentService
import com.dogancaglar.paymentservice.application.util.PaymentFactory
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceReadUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.ports.outbound.PspAuthGatewayPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentServiceConfig {



    @Bean
    fun paymentOrderDomainEventMapper(clock: Clock): PaymentOrderDomainEventMapper =
        PaymentOrderDomainEventMapper(clock)

    @Bean
    fun paymentFactory(clock: Clock): PaymentFactory =
        PaymentFactory(clock)

    @Bean
    fun createAuthorizePaymentService(
        idGeneratorPort: RedisIdGeneratorPortAdapter,
        paymentRepository: PaymentOutboundAdapter,
        outboxOutboundAdapter: OutboxOutboundAdapter,
        serializationPort: JacksonSerializationAdapter,
        clock: Clock,
        paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
        pspAuthGatewayPort: PspAuthGatewayPort
    ): AuthorizePaymentService {
        return AuthorizePaymentService(
            idGeneratorPort = idGeneratorPort,
            paymentRepository = paymentRepository,
            psp = pspAuthGatewayPort,
            outboxEventPort = outboxOutboundAdapter,
            serializationPort = serializationPort,
            clock = clock,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper
        )
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