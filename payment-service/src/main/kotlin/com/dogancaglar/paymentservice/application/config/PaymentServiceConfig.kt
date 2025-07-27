package com.dogancaglar.paymentservice.config.application


import com.dogancaglar.infrastructure.redis.PspResultRedisCacheAdapter
import com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.outbound.persistance.OutboxBufferAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistance.PaymentOrderOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistance.PaymentOrderStatusCheckAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistance.PaymentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.PaymentRetryQueueAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.RedisIdGeneratorPortAdapter
import com.dogancaglar.paymentservice.adapter.outbound.serialization.JacksonSerializationAdapter
import com.dogancaglar.paymentservice.application.usecases.CreatePaymentService
import com.dogancaglar.paymentservice.domain.config.ProcessPaymentService
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.paymentservice.serialization.JacksonUtil
import com.fasterxml.jackson.databind.ObjectMapper
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
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper = JacksonUtil.createObjectMapper()
}


@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}