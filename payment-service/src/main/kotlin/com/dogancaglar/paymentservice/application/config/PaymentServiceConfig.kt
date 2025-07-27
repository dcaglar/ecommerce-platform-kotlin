package com.dogancaglar.paymentservice.application.config


import com.dogancaglar.com.dogancaglar.payment.application.port.out.SerializationPort
import com.dogancaglar.paymentservice.application.usecases.CreatePaymentService
import com.dogancaglar.paymentservice.application.usecases.ProcessPaymentService
import com.dogancaglar.paymentservice.domain.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.paymentservice.ports.outbound.*
import com.dogancaglar.paymentservice.serialization.JacksonUtil
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentServiceConfig {

    @Bean
    fun createPaymentService(
        idGeneratorPort: IdGeneratorPort,
        paymentRepository: PaymentRepository,
        paymentOrderRepository: PaymentOrderRepository,
        outboxEventPort: OutboxEventPort,
        serializationPort: SerializationPort,
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
        paymentOrderRepository: PaymentOrderRepository,
        paymentEventPublisher: EventPublisherPort,
        paymentRetryQueueAdapter: RetryQueuePort<PaymentOrderRetryRequested>,
        paymentOrderStatusCheckAdapter: PaymentOrderStatusCheckRepository,
        pspResultRedisCacheAdapter: PspResultCachePort,
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

    @Bean("myObjectMapper")
    fun objectMapper(): ObjectMapper = JacksonUtil.createObjectMapper()
}


@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
