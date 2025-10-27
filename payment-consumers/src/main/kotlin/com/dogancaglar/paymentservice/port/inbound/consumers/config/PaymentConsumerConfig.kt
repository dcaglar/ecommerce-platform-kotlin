package com.dogancaglar.paymentservice.port.inbound.consumers.config


import com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.outbound.redis.PaymentRetryQueueAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.PspResultRedisCacheAdapter
import com.dogancaglar.paymentservice.application.usecases.ProcessPaymentService
import com.dogancaglar.paymentservice.domain.util.PaymentFactory
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentConsumerConfig {


    @Bean
    fun paymentOrderDomainEventMapper(clock: Clock): PaymentOrderDomainEventMapper =
        PaymentOrderDomainEventMapper(clock)

    @Bean
    fun paymentOrderFactory(): PaymentOrderFactory =
        PaymentOrderFactory()


    @Bean
    fun processPaymentService(
        paymentOrderModificationPort: PaymentOrderModificationPort,
        @Qualifier("syncPaymentEventPublisher") syncPaymentEventPublisher: PaymentEventPublisher,
        paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
        clock: Clock,
        paymentOrderFactory: PaymentOrderFactory,
        paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
    ): ProcessPaymentService {
        return ProcessPaymentService(
            paymentOrderModificationPort = paymentOrderModificationPort,
            eventPublisher = syncPaymentEventPublisher,
            retryQueuePort = paymentRetryQueueAdapter,
            clock = clock,
            paymentOrderFactory = paymentOrderFactory,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper
        )
    }


    @Bean
    fun createPaymentFactory(clock: Clock) =
        PaymentFactory(clock = clock)

    @Bean
    fun createPaymentOrderFactory(clock: Clock) =
        PaymentOrderFactory()
}