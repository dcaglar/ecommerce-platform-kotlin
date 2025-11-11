package com.dogancaglar.paymentservice.application.config

import com.dogancaglar.paymentservice.adapter.outbound.persistance.OutboxBufferAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistance.PaymentOrderOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistance.PaymentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.RedisIdGeneratorPortAdapter
import com.dogancaglar.paymentservice.adapter.outbound.serialization.JacksonSerializationAdapter
import com.dogancaglar.paymentservice.application.usecases.AccountBalanceReadService
import com.dogancaglar.paymentservice.application.usecases.CreatePaymentService
import com.dogancaglar.paymentservice.application.util.PaymentFactory
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceReadUseCase
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
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
    fun createPaymentService(
        idGeneratorPort: RedisIdGeneratorPortAdapter,
        paymentRepository: PaymentOutboundAdapter,
        paymentOrderRepository: PaymentOrderOutboundAdapter,
        outboxEventPort: OutboxBufferAdapter,
        serializationPort: JacksonSerializationAdapter,
        clock: Clock,
        paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
        paymentFactory: PaymentFactory
    ): CreatePaymentUseCase {
        return CreatePaymentService(
            idGeneratorPort = idGeneratorPort,
            paymentRepository = paymentRepository,
            paymentOrderRepository = paymentOrderRepository,
            outboxEventPort = outboxEventPort,
            serializationPort = serializationPort,
            clock = clock,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper,
            paymentFactory = paymentFactory
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