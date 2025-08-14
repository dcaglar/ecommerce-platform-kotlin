package com.dogancaglar.paymentservice.application.config


import com.dogancaglar.paymentservice.adapter.outbound.persistance.OutboxBufferAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistance.PaymentOrderOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistance.PaymentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.RedisIdGeneratorPortAdapter
import com.dogancaglar.paymentservice.adapter.outbound.serialization.JacksonSerializationAdapter
import com.dogancaglar.paymentservice.application.usecases.CreatePaymentService
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
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
}