package com.dogancaglar.consumers.config


import com.dogancaglar.infrastructure.adapter.persistance.OutboxBufferAdapter
import com.dogancaglar.infrastructure.adapter.persistance.PaymentOrderOutboundAdapter
import com.dogancaglar.infrastructure.adapter.persistance.PaymentOrderStatusCheckAdapter
import com.dogancaglar.infrastructure.adapter.persistance.PaymentOutboundAdapter
import com.dogancaglar.infrastructure.adapter.producers.PaymentEventPublisher
import com.dogancaglar.infrastructure.adapter.serialization.JacksonSerializationAdapter
import com.dogancaglar.infrastructure.redis.PaymentRetryQueueAdapter
import com.dogancaglar.infrastructure.redis.PspResultRedisCacheAdapter
import com.dogancaglar.infrastructure.redis.id.RedisIdGeneratorPortAdapter
import com.dogancaglar.payment.application.service.CreatePaymentService
import com.dogancaglar.payment.application.service.ProcessPaymentService
import com.dogancaglar.payment.domain.factory.PaymentFactory
import com.dogancaglar.payment.domain.factory.PaymentOrderFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentProcessorServiceConfig {


    @Bean
    fun processPaymentService(
        paymentOrderRepository: PaymentOrderOutboundAdapter,
        paymentEventPublisher: PaymentEventPublisher,
        paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
        paymentOrderStatusCheckAdapter: PaymentOrderStatusCheckAdapter,
        pspResultRedisCacheAdapter: PspResultRedisCacheAdapter,
        outboxEventPort: OutboxBufferAdapter,
        clock: Clock,
    ): ProcessPaymentService {
        return ProcessPaymentService(
            paymentOrderRepository = paymentOrderRepository,
            eventPublisher = paymentEventPublisher,
            retryQueuePort = paymentRetryQueueAdapter,
            statusCheckRepo = paymentOrderStatusCheckAdapter,
            pspResultCache = pspResultRedisCacheAdapter,
            clock = clock
        )
    }

    @Bean
    fun paymentService(
        idGeneratorPort: RedisIdGeneratorPortAdapter,
        paymentRepository: PaymentOutboundAdapter,
        paymentOrderRepository: PaymentOrderOutboundAdapter,
        outboxEventPort: OutboxBufferAdapter,
        serializationPort: JacksonSerializationAdapter,
        clock: Clock,
    ): CreatePaymentService {
        return CreatePaymentService(
            idGeneratorPort,
            paymentRepository,
            paymentOrderRepository,
            outboxEventPort,
            serializationPort,
            clock
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