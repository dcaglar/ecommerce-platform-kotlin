package com.dogancaglar.paymentservice.config


import com.dogancaglar.infrastructure.adapter.persistance.OutboxBufferAdapter
import com.dogancaglar.infrastructure.adapter.persistance.PaymentOrderOutboundAdapter
import com.dogancaglar.infrastructure.adapter.persistance.PaymentOrderStatusCheckAdapter
import com.dogancaglar.infrastructure.adapter.persistance.PaymentOutboundAdapter
import com.dogancaglar.infrastructure.adapter.producers.PaymentEventPublisher
import com.dogancaglar.infrastructure.adapter.serialization.JacksonSerializationAdapter
import com.dogancaglar.infrastructure.redis.PaymentRetryQueueAdapter
import com.dogancaglar.infrastructure.redis.PspResultRedisCacheAdapter
import com.dogancaglar.infrastructure.redis.id.RedisIdGeneratorPortAdapter
import com.dogancaglar.payment.application.command.CreatePaymentCommand
import com.dogancaglar.payment.application.command.ProcessPaymentCommand
import com.dogancaglar.payment.application.port.inbound.CreatePaymentUseCase
import com.dogancaglar.payment.domain.factory.PaymentFactory
import com.dogancaglar.payment.domain.factory.PaymentOrderFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentServiceConfig {

    @Bean
    fun createPaymentCommand(
        idGeneratorPort: RedisIdGeneratorPortAdapter,
        paymentRepository: PaymentOutboundAdapter,
        paymentOrderRepository: PaymentOrderOutboundAdapter,
        outboxEventPort: OutboxBufferAdapter,
        serializationPort: JacksonSerializationAdapter,
        clock: Clock,
    ): CreatePaymentUseCase {
        return CreatePaymentCommand(
            idGeneratorPort,
            paymentRepository,
            paymentOrderRepository,
            outboxEventPort,
            serializationPort,
            clock
        )
    }

    @Bean
    fun processPaymentCommand(
        paymentOrderRepository: PaymentOrderOutboundAdapter,
        paymentEventPublisher: PaymentEventPublisher,
        paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
        paymentOrderStatusCheckAdapter: PaymentOrderStatusCheckAdapter,
        pspResultRedisCacheAdapter: PspResultRedisCacheAdapter,
        outboxEventPort: OutboxBufferAdapter,
        clock: Clock,
    ): ProcessPaymentCommand {
        return ProcessPaymentCommand(
            paymentOrderRepository = paymentOrderRepository,
            eventPublisher = paymentEventPublisher,
            retryQueuePort = paymentRetryQueueAdapter,
            statusCheckRepo = paymentOrderStatusCheckAdapter,
            pspResultCache = pspResultRedisCacheAdapter,
            clock = clock
        )
    }


    @Bean
    fun createPaymentFactory(clock: Clock) =
        PaymentFactory(clock = clock)

    @Bean
    fun createPaymentOrderFactory(clock: Clock) =
        PaymentOrderFactory()
}


@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}