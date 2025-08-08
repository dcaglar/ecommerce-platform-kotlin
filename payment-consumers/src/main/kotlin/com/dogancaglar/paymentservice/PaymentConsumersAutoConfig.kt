package com.dogancaglar.paymentservice

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

@AutoConfiguration
@EnableConfigurationProperties(
    com.dogancaglar.paymentservice.consumers.DynamicKafkaConsumersProperties::class
)
@Import(
    com.dogancaglar.paymentservice.consumers.KafkaTypedConsumerFactoryConfig::class
)
class PaymentConsumersAutoConfig