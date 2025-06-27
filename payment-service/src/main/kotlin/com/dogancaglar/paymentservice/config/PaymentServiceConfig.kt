package com.dogancaglar.paymentservice.config


import com.dogancaglar.com.dogancaglar.payment.application.port.out.SerializationPort
import com.dogancaglar.payment.application.port.outbound.OutboxEventPort
import com.dogancaglar.payment.application.service.CreatePaymentService
import com.dogancaglar.payment.application.service.ProcessPaymentService
import com.dogancaglar.payment.domain.factory.PaymentFactory
import com.dogancaglar.payment.domain.factory.PaymentOrderFactory
import com.dogancaglar.payment.domain.port.PaymentRepository
import com.dogancaglar.payment.domain.port.id.IdGeneratorPort
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.persistence.PaymentOrderStatusCheckAdapter
import com.dogancaglar.paymentservice.adapter.redis.PaymentRetryQueueAdapter
import com.dogancaglar.paymentservice.adapter.redis.PspResultRedisCacheAdapter
import com.dogancaglar.port.PaymentOrderRepository
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
    fun createProcessPaymentService(
        paymentOrderRepository: PaymentOrderRepository,
        paymentEventPublisher: PaymentEventPublisher,
        paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
        paymentOrderStatusCheckAdapter: PaymentOrderStatusCheckAdapter,
        pspResultRedisCacheAdapter: PspResultRedisCacheAdapter,
        outboxEventPort: OutboxEventPort,
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