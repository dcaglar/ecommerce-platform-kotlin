package com.dogancaglar.paymentservice.infrastructure

import com.dogancaglar.paymentservice.redis.RedisConfig
import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import

@AutoConfiguration
@Import(
    RedisConfig::class,
    // etc...
)
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis")
class PaymentInfrastructureAutoConfig