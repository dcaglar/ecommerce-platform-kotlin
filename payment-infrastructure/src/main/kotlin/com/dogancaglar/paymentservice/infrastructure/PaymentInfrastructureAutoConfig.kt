package com.dogancaglar.paymentservice.infrastructure

import com.dogancaglar.paymentservice.redis.RedisConfig
import com.dogancaglar.paymentservice.snowflake.IdGenerationProperties
import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

@AutoConfiguration
@EnableConfigurationProperties(IdGenerationProperties::class)
@Import(
    RedisConfig::class,
    // etc...
)
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis")
class PaymentInfrastructureAutoConfig