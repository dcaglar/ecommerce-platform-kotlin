package com.dogancaglar.paymentservice.infrastructure

import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan(
    basePackages = [
        // limit to infra subtrees you just moved
        "com.dogancaglar.paymentservice.adapter.outbound.persistance",
        "com.dogancaglar.paymentservice.consumers",       // if you want, optional
        "com.dogancaglar.paymentservice.deserialization",
        "com.dogancaglar.paymentservice.kafka",
        "com.dogancaglar.paymentservice.metrics",
        "com.dogancaglar.paymentservice.redis",
        "com.dogancaglar.paymentservice.serialization"
    ]
)
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis")
class PaymentInfrastructureAutoConfig