package com.dogancaglar.paymentservice.port.inbound.consumers.config


import com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.outbound.redis.PaymentRetryQueueAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.PspResultRedisCacheAdapter
import com.dogancaglar.paymentservice.application.usecases.ProcessPaymentService
import com.dogancaglar.paymentservice.domain.util.PaymentFactory
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderStatePort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentProcessorServiceConfig {


    @Bean
    fun processPaymentService(
        paymentOrderStatePort: PaymentOrderStatePort,
        paymentEventPublisher: PaymentEventPublisher,
        paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
        pspResultRedisCacheAdapter: PspResultRedisCacheAdapter,
        clock: Clock,
    ): ProcessPaymentService {
        return ProcessPaymentService(
            paymentOrderStatePort = paymentOrderStatePort,
            eventPublisher = paymentEventPublisher,
            retryQueuePort = paymentRetryQueueAdapter,
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