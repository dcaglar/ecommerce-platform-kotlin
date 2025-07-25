package com.dogancaglar.paymentservice.config.application


import com.dogancaglar.infrastructure.redis.PaymentRetryQueueAdapter
import com.dogancaglar.infrastructure.redis.PspResultRedisCacheAdapter
import com.dogancaglar.infrastructure.redis.id.RedisIdGeneratorPortAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.outbound.redis.persistance.PaymentOutboundAdapter
import com.dogancaglar.paymentservice.domain.config.CreatePaymentService
import com.dogancaglar.paymentservice.domain.config.ProcessPaymentService
import com.dogancaglar.paymentservice.port.inbound.CreatePaymentUseCase
import com.dogancaglar.port.out.adapter.persistance.OutboxBufferAdapter
import com.dogancaglar.port.out.adapter.persistance.PaymentOrderOutboundAdapter
import com.dogancaglar.port.out.adapter.persistance.PaymentOrderStatusCheckAdapter
import com.dogancaglar.port.out.adapter.serialization.JacksonSerializationAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentServiceConfig {

    @Bean
    fun createPaymentService(
        idGeneratorPort: RedisIdGeneratorPortAdapter,
        paymentRepository: PaymentOutboundAdapter,
        paymentOrderRepository: PaymentOrderOutboundAdapter,
        outboxEventPort: OutboxBufferAdapter,
        serializationPort: JacksonSerializationAdapter,
        clock: Clock,
    ): CreatePaymentUseCase {
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
    fun processPaymentService(
        paymentOrderRepository: PaymentOrderOutboundAdapter,
        paymentEventPublisher: PaymentEventPublisher,
        paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
        paymentOrderStatusCheckAdapter: PaymentOrderStatusCheckAdapter,
        pspResultRedisCacheAdapter: PspResultRedisCacheAdapter,
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
}


@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}