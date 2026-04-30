package com.dogancaglar.paymentinfra

import com.dogancaglar.paymentservice.infra.adapter.outbound.id.config.IdGenerationProperties
import com.dogancaglar.paymentservice.infra.adapter.outbound.redis.config.RedisConfig
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
@MapperScan(
    basePackages = ["com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper"],
)
class PaymentInfrastructureAutoConfig