package com.dogancaglar.paymentservice.port.inbound.consumers.config


import com.dogancaglar.paymentservice.domain.config.ProcessPaymentService
import com.dogancaglar.paymentservice.domain.util.PaymentFactory
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentProcessorServiceConfig {


    @Bean
    fun processPaymentCommand(
    ): ProcessPaymentService? {
        return null;

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